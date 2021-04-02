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
import com.exactpro.th2.infraoperator.model.kubernetes.client.impl.LinkClient;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.Th2LinkSpec;
import com.exactpro.th2.infraoperator.spec.link.relation.BoxesRelation;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.DictionaryBinding;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCoupling;
import com.exactpro.th2.infraoperator.spec.shared.Identifiable;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.RESYNC_TIME;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;

public class Th2LinkEventHandler implements Watcher<Th2Link> {
    private static final Logger logger = LoggerFactory.getLogger(Th2LinkEventHandler.class);

    private LinkClient linkClient;

    public LinkClient getLinkClient() {
        return linkClient;
    }

    public static Th2LinkEventHandler newInstance(SharedInformerFactory sharedInformerFactory,
                                                  KubernetesClient client,
                                                  EventQueue eventQueue) {
        var res = new Th2LinkEventHandler();
        res.linkClient = new LinkClient(client);

        SharedIndexInformer<Th2Link> linkInformer = sharedInformerFactory.sharedIndexInformerForCustomResource(
                Th2Link.class, RESYNC_TIME);

        linkInformer.addEventHandler(new GenericResourceEventHandler<>(res, eventQueue));

        return res;
    }

    private <T extends Identifiable> void checkForDuplicates(List<T> links, String annotation) {

        Set<String> linkNames = new HashSet<>();
        Set<String> linkIds = new HashSet<>();
        for (var link : links) {
            if (!linkNames.add(link.getName())) {
                logger.warn("Link with name \"{}\" already exists in the \"{}\"", link.getName(), annotation);
            }
            if (!linkIds.add(link.getId())) {
                logger.warn("Link with id \"{}\" already exists in the \"{}\"", link.getId(), annotation);
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

    private int refreshAffectedBoxes(Th2Link prevLink, Th2Link newLink) {

        String namespace = extractNamespace(prevLink);
        namespace = namespace != null ? namespace : extractNamespace(newLink);

        Set<String> boxesNamesToUpdate = getAffectedBoxNames(prevLink, newLink);
        int items = boxesNamesToUpdate.size();
        if (items == 0) {
            logger.info("No boxes needs to be updated");
            return 0;
        } else {
            logger.info("{} box(es) needs to be updated", items);
            return DefaultWatchManager.getInstance().refreshBoxes(namespace, boxesNamesToUpdate);
        }
    }

    private Set<String> getAffectedBoxNames(Th2Link prevLink, Th2Link newLink) {

        // collect box names affected by router link changes
        List<PinCoupling> prevLinkCouplings = prevLink.getSpec().getBoxesRelation().getAllLinks();
        List<PinCoupling> newLinkCouplings = newLink.getSpec().getBoxesRelation().getAllLinks();

        Set<String> affectedByRouterLinks = getAffectedBoxNamesByList(prevLinkCouplings, newLinkCouplings,
                pinCoupling -> Set.of(pinCoupling.getFrom().getBoxName(), pinCoupling.getTo().getBoxName()));

        // collect box names affected by dictionary binding changes
        List<DictionaryBinding> prevDictionaryBindings = prevLink.getSpec().getDictionariesRelation();
        List<DictionaryBinding> newDictionaryBindings = newLink.getSpec().getDictionariesRelation();

        Set<String> affectedByDictionaryBindings = getAffectedBoxNamesByList(prevDictionaryBindings,
                newDictionaryBindings, binding -> Set.of(binding.getBox()));

        // join two sets
        affectedByRouterLinks.addAll(affectedByDictionaryBindings);
        return affectedByRouterLinks;
    }

    @Override
    public void eventReceived(Action action, Th2Link th2Link) {

        String namespace = extractNamespace(th2Link);
        OperatorState operatorState = OperatorState.INSTANCE;
        var lock = operatorState.getLock(namespace);
        try {
            lock.lock();

            checkForDuplicates(th2Link);
            removeInvalidLinks(th2Link);

            var linkResources = new ArrayList<>(operatorState.getLinkResources(namespace));

            Th2Link prevLink = getPreviousLink(th2Link, linkResources);
            linkResources.remove(th2Link);
            if (action.equals(Action.DELETED)) {
                refreshAffectedBoxes(prevLink, Th2Link.newInstance());
            } else {
                refreshAffectedBoxes(prevLink, th2Link);
                linkResources.add(th2Link);
            }

            operatorState.setLinkResources(namespace, linkResources);
        } finally {
            lock.unlock();
        }

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
