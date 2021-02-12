package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.model.kubernetes.client.ipml.LinkClient;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.Th2LinkList;
import com.exactpro.th2.infraoperator.spec.link.Th2LinkSpec;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.DictionaryBinding;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCoupling;
import com.exactpro.th2.infraoperator.spec.shared.Identifiable;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
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

import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;

public class Th2LinkEventHandler implements WatchHandler<Th2Link> {
    private static final Logger logger = LoggerFactory.getLogger(Th2LinkEventHandler.class);

    public static Th2LinkEventHandler newInstance(SharedInformerFactory sharedInformerFactory,
                                                  KubernetesClient client,
                                                  DefaultWatchManager.EventContainer<DefaultWatchManager.DispatcherEvent> eventContainer) {
        var linkClient = new LinkClient(client);

        SharedIndexInformer<Th2Link> linkInformer = sharedInformerFactory.sharedIndexInformerForCustomResource(
                CustomResourceDefinitionContext.fromCrd(linkClient.getCustomResourceDefinition()),
                Th2Link.class,
                Th2LinkList.class,
                CustomResourceUtils.RESYNC_TIME);

        var res = new Th2LinkEventHandler();
        linkInformer.addEventHandlerWithResyncPeriod(CustomResourceUtils.resourceEventHandlerFor(
                res,
                Th2Link.class,
                linkClient.getCustomResourceDefinition(),
                eventContainer),
                0);

        return res;
    }


    @Override
    public void onAdd(Th2Link th2Link) {

        String namespace = extractNamespace(th2Link);
        OperatorState operatorState = OperatorState.INSTANCE;
        var lock = operatorState.getLock(namespace);
        try {
            lock.lock();

            checkForDuplicates(th2Link);

            var linkResources = new ArrayList<>(operatorState.getLinkResources(namespace));
            Th2Link prevLink = getPreviousLink(th2Link, linkResources);
            refreshAffectedBoxes(prevLink, th2Link);
            linkResources.remove(th2Link);
            linkResources.add(th2Link);

            operatorState.setLinkResources(namespace, linkResources);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onUpdate(Th2Link oldTh2Link, Th2Link newTh2Link) {

        String namespace = extractNamespace(newTh2Link);
        OperatorState operatorState = OperatorState.INSTANCE;
        var lock = operatorState.getLock(namespace);
        try {
            lock.lock();

            checkForDuplicates(newTh2Link);

            var linkResources = new ArrayList<>(operatorState.getLinkResources(namespace));

            Th2Link prevLink = getPreviousLink(newTh2Link, linkResources);
            refreshAffectedBoxes(prevLink, newTh2Link);
            linkResources.remove(newTh2Link);
            linkResources.add(newTh2Link);

            operatorState.setLinkResources(namespace, linkResources);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onDelete(Th2Link th2Link, boolean deletedFinalStateUnknown) {

        String namespace = extractNamespace(th2Link);
        OperatorState operatorState = OperatorState.INSTANCE;
        var lock = operatorState.getLock(namespace);
        try {
            lock.lock();

            checkForDuplicates(th2Link);

            var linkResources = new ArrayList<>(operatorState.getLinkResources(namespace));
            Th2Link prevLink = getPreviousLink(th2Link, linkResources);
            refreshAffectedBoxes(prevLink, Th2Link.newInstance());
            linkResources.remove(th2Link);

            //TODO: Error case not covered
            operatorState.setLinkResources(namespace, linkResources);
        } finally {
            lock.unlock();
        }
    }


    private <T extends Identifiable> void checkForDuplicates(List<T> links, String annotation) {

        Set<String> linkNames = new HashSet<>();
        Set<String> linkIds = new HashSet<>();
        for (var link : links) {
            if (!linkNames.add(link.getName()))
                logger.warn("Link with name \"{}\" already exists in the \"{}\"", link.getName(), annotation);
            if (!linkIds.add(link.getId()))
                logger.warn("Link with id \"{}\" already exists in the \"{}\"", link.getId(), annotation);
        }
    }


    private void checkForDuplicates(Th2Link th2Link) {

        String resourceLabel = annotationFor(th2Link);
        Th2LinkSpec spec = th2Link.getSpec();
        checkForDuplicates(spec.getBoxesRelation().getRouterMq(), resourceLabel);
        checkForDuplicates(spec.getBoxesRelation().getRouterGrpc(), resourceLabel);
        checkForDuplicates(spec.getDictionariesRelation(), resourceLabel);
    }


    private Th2Link getPreviousLink(Th2Link th2Link, List<Th2Link> linkResources) {

        int index = linkResources.indexOf(th2Link);
        return index < 0 ? Th2Link.newInstance() : linkResources.get(index);
    }


    private int refreshAffectedBoxes(Th2Link prevLink, Th2Link newLink) {

        String namespace = extractNamespace(newLink);
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

        Set<String> affectedByDictionaryBindings = getAffectedBoxNamesByList(prevDictionaryBindings, newDictionaryBindings,
                binding -> Set.of(binding.getBox()));

        // join two sets
        affectedByRouterLinks.addAll(affectedByDictionaryBindings);
        return affectedByRouterLinks;
    }

    @Override
    public void eventReceived(Action action, Th2Link resource) {

    }

    @Override
    public void onClose(WatcherException cause) {

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
}

