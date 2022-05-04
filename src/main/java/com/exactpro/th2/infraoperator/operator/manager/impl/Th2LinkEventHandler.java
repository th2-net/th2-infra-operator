/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.metrics.OperatorMetrics;
import com.exactpro.th2.infraoperator.model.kubernetes.client.impl.LinkClient;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.Th2LinkSpec;
import com.exactpro.th2.infraoperator.spec.link.relation.BoxesRelation;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.DictionaryBinding;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.MultiDictionaryBinding;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCoupling;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingGRPC;
import com.exactpro.th2.infraoperator.spec.shared.Identifiable;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.BindQueueLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.NonTerminalException;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.RetryableTaskQueue;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.tasks.TriggerRedeployTask;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.prometheus.client.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.RESYNC_TIME;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;

public class Th2LinkEventHandler implements Watcher<Th2Link> {
    private static final Logger logger = LoggerFactory.getLogger(Th2LinkEventHandler.class);

    private static final int REDEPLOY_DELAY = 120;

    private final RetryableTaskQueue retryableTaskQueue = new RetryableTaskQueue();

    private KubernetesClient kubClient;

    public LinkClient getLinkClient() {
        return new LinkClient(kubClient);
    }

    private final Map<String, String> sourceHashes = new ConcurrentHashMap<>();

    public static Th2LinkEventHandler newInstance(SharedInformerFactory sharedInformerFactory,
                                                  KubernetesClient client,
                                                  EventQueue eventQueue) {
        var res = new Th2LinkEventHandler();
        res.kubClient = client;

        SharedIndexInformer<Th2Link> linkInformer = sharedInformerFactory.sharedIndexInformerFor(
                Th2Link.class, RESYNC_TIME);

        linkInformer.addEventHandler(new GenericResourceEventHandler<>(res, eventQueue));

        return res;
    }

    @Override
    public void eventReceived(Action action, Th2Link th2Link) {

        String namespace = extractNamespace(th2Link);
        OperatorState operatorState = OperatorState.INSTANCE;
        String resourceLabel = annotationFor(th2Link);
        String sourceHash = ExtractUtils.fullSourceHash(th2Link);
        String prevHash = sourceHashes.get(resourceLabel);

        if (action == Action.MODIFIED && prevHash != null && prevHash.equals(sourceHash)) {
            logger.info("Link: \"{}\" has not been changed", resourceLabel);
            return;
        }
        Histogram.Timer processTimer = OperatorMetrics.getCustomResourceEventTimer(th2Link);
        logger.info("Updating all boxes and bindings related to \"{}\"", resourceLabel);

        var lock = operatorState.getLock(namespace);
        try {
            lock.lock();

            checkForDuplicates(th2Link);
            removeInvalidLinks(th2Link);

            var linkResources = new ArrayList<>(operatorState.getLinkResources(namespace));

            Th2Link prevLink = getPreviousLink(th2Link, linkResources);
            linkResources.remove(th2Link);
            if (action == Action.DELETED || action == Action.ERROR) {
                BindQueueLinkResolver.resolveLinkResource(namespace, prevLink, Th2Link.newInstance());
                refreshAffectedBoxes(prevLink, Th2Link.newInstance());
                sourceHashes.remove(resourceLabel);
            } else {
                BindQueueLinkResolver.resolveLinkResource(namespace, prevLink, th2Link);
                refreshAffectedBoxes(prevLink, th2Link);
                linkResources.add(th2Link);
                sourceHashes.put(resourceLabel, sourceHash);
            }

            operatorState.setLinkResources(namespace, linkResources);
        } catch (NonTerminalException e) {
            logger.error("Non-terminal Exception processing {} event for \"{}\". Will try to redeploy.",
                    action, resourceLabel, e);


            Namespace namespaceObj = kubClient.namespaces().withName(namespace).get();
            if (namespaceObj == null || !namespaceObj.getStatus().getPhase().equals("Active")) {
                logger.info("Namespace \"{}\" deleted or not active, cancelling", namespace);
                return;
            }

            //create and schedule task to redeploy failed component
            TriggerRedeployTask triggerRedeployTask = new TriggerRedeployTask(this,
                    getLinkClient(), kubClient, th2Link, action, REDEPLOY_DELAY);
            retryableTaskQueue.add(triggerRedeployTask, true);

            logger.info("Task \"{}\" added to scheduler, with delay \"{}\" seconds",
                    triggerRedeployTask.getName(), REDEPLOY_DELAY);
        } catch (Exception e) {
            logger.error("Terminal Exception processing {} event for {}. Will not try to redeploy",
                    action, resourceLabel, e);
        } finally {
            //observe event processing time for only operator
            processTimer.observeDuration();
            //observe time it took to process event only by both manager and operator
            OperatorMetrics.observeTotal(th2Link);
            lock.unlock();
        }

    }

    private <T extends Identifiable> void checkForDuplicates(List<T> links, String annotation) {

        Set<String> linkNames = new HashSet<>();
        Set<String> linkIds = new HashSet<>();
        for (var link : links) {
            boolean sameName = !linkNames.add(link.getName());
            boolean sameContent = !linkIds.add(link.getId());
            if (sameName && sameContent) {
                logger.warn("There are multiple links with same name \"{}\" and same content \"{}\" in the \"{}\"",
                        link.getName(), link.getId(), annotation);
            } else if (sameName) {
                logger.warn("There are multiple links with same name \"{}\" but different content in the \"{}\"",
                        link.getName(), annotation);
            } else if (sameContent) {
                logger.warn("There are multiple links with same content \"{}\" but different names in the \"{}\"",
                        link.getId(), annotation);
            }
        }
    }

    private <T extends PinCoupling> List<T> removeInvalidLinks(List<T> links, String annotation) {
        List<T> validLinks = new ArrayList<>();
        for (var link : links) {
            if (link.getTo().getBoxName().equals(link.getFrom().getBoxName())) {
                logger.warn("Skipping invalid link \"{}\" in the \"{}\". " +
                        "\"from\" box name can not be the same as \"to\" box name", link.getName(), annotation);
            } else {
                validLinks.add(link);
            }
        }
        return validLinks;
    }

    private void checkForDuplicates(Th2Link th2Link) {

        String resourceLabel = annotationFor(th2Link);
        Th2LinkSpec spec = th2Link.getSpec();
        checkForDuplicates(spec.getBoxesRelation().getRouterMq(), resourceLabel);
        checkForDuplicates(spec.getBoxesRelation().getRouterGrpc(), resourceLabel);
        checkForDuplicates(spec.getDictionariesRelation(), resourceLabel);
        checkForDuplicates(spec.getMultiDictionariesRelation(), resourceLabel);
    }

    private void removeInvalidLinks(Th2Link th2Link) {
        String resourceLabel = annotationFor(th2Link);
        Th2LinkSpec spec = th2Link.getSpec();
        BoxesRelation boxesRelation = spec.getBoxesRelation();
        boxesRelation.setRouterMq(removeInvalidLinks(boxesRelation.getRouterMq(), resourceLabel));
        boxesRelation.setRouterGrpc(removeInvalidLinks(boxesRelation.getRouterGrpc(), resourceLabel));
    }

    private Th2Link getPreviousLink(Th2Link th2Link, List<Th2Link> linkResources) {

        int index = linkResources.indexOf(th2Link);
        return index < 0 ? Th2Link.newInstance() : linkResources.get(index);
    }

    private void refreshAffectedBoxes(Th2Link prevLink, Th2Link newLink) {

        String namespace = extractNamespace(prevLink);
        namespace = namespace != null ? namespace : extractNamespace(newLink);

        Set<String> boxesNamesToUpdate = getAffectedBoxNames(prevLink, newLink);
        int items = boxesNamesToUpdate.size();
        if (items == 0) {
            logger.info("No boxes needs to be updated");
            return;
        }
        Namespace namespaceObj = kubClient.namespaces().withName(namespace).get();
        if (namespaceObj == null || !namespaceObj.getStatus().getPhase().equals("Active")) {
            logger.info("Namespace \"{}\" deleted or not active, cancelling", namespace);
            return;
        }
        logger.info("{} box(es) needs to be updated", items);
        DefaultWatchManager.getInstance().refreshBoxes(namespace, boxesNamesToUpdate);

    }

    private Set<String> getAffectedBoxNames(Th2Link prevLink, Th2Link newLink) {

        // collect box names affected by grpc link changes
        List<PinCouplingGRPC> prevLinkCouplings = prevLink.getSpec().getBoxesRelation().getRouterGrpc();
        List<PinCouplingGRPC> newLinkCouplings = newLink.getSpec().getBoxesRelation().getRouterGrpc();

        Set<String> affectedByRouterLinks = getAffectedBoxNamesByList(prevLinkCouplings, newLinkCouplings,
                pinCoupling -> Set.of(pinCoupling.getFrom().getBoxName(), pinCoupling.getTo().getBoxName()));

        // collect box names affected by dictionary binding changes
        List<DictionaryBinding> prevDictionaryBindings = prevLink.getSpec().getDictionariesRelation();
        List<DictionaryBinding> newDictionaryBindings = newLink.getSpec().getDictionariesRelation();

        Set<String> affectedByDictionaryBindings = getAffectedBoxNamesByList(prevDictionaryBindings,
                newDictionaryBindings, binding -> Set.of(binding.getBox()));

        // collect box names affected by multi dictionary binding changes
        List<MultiDictionaryBinding> prevMultiDictionaryBindings = prevLink.getSpec().getMultiDictionariesRelation();
        List<MultiDictionaryBinding> newMultiDictionaryBindings = newLink.getSpec().getMultiDictionariesRelation();

        Set<String> affectedByMultiDictionaryBindings = getAffectedBoxNamesByList(prevMultiDictionaryBindings,
                newMultiDictionaryBindings, binding -> Set.of(binding.getBox()));

        // join three sets
        affectedByRouterLinks.addAll(affectedByDictionaryBindings);
        affectedByRouterLinks.addAll(affectedByMultiDictionaryBindings);
        return affectedByRouterLinks;
    }

    <T extends Identifiable> Set<String> getAffectedBoxNamesByList(List<T> prevLinks, List<T> newLinks,
                                                                   Function<T, Set<String>> boxesExtractor) {

        // filter out links that have not changed
        // all the boxes that were associated with changed links needs to be updated

        Set<String> affectedBoxes = new HashSet<>();

        // collect box names from previous links, that will need to be updated
        affectedBoxes.addAll(prevLinks.stream()
                .filter(f -> newLinks.stream().noneMatch(s -> s.equals(f)))
                .flatMap(t -> boxesExtractor.apply(t).stream())
                .collect(Collectors.toSet())
        );

        // collect box names from current links, that will need to be updated
        affectedBoxes.addAll(newLinks.stream()
                .filter(f -> prevLinks.stream().noneMatch(s -> s.equals(f)))
                .flatMap(t -> boxesExtractor.apply(t).stream())
                .collect(Collectors.toSet())
        );

        return affectedBoxes;
    }

    @Override
    public void onClose(WatcherException cause) {
        throw new AssertionError("This method should not be called");
    }
}
