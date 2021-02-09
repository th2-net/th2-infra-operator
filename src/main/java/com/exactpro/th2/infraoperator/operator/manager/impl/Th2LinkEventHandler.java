package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.model.kubernetes.client.ipml.LinkClient;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.Th2LinkList;
import com.exactpro.th2.infraoperator.spec.shared.Identifiable;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
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

public class Th2LinkEventHandler implements ResourceEventHandler<Th2Link> {
    private static final Logger logger = LoggerFactory.getLogger(Th2LinkEventHandler.class);

    public static Th2LinkEventHandler newInstance(SharedInformerFactory sharedInformerFactory, KubernetesClient client) {
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
                linkClient.getCustomResourceDefinition()),
                0);

        return res;
    }

    private <T extends Identifiable> void checkForDuplicateNames(List<T> links, String annotation) {
        Set<String> linkNames = new HashSet<>();
        for (var link : links) {
            if (!linkNames.add(link.getName())) {
                logger.warn("Link with name: {} already exists in {}", link.getName(), annotation);
            }
        }
    }

    @Override
    public void onAdd(Th2Link th2Link) {

        var linkNamespace = extractNamespace(th2Link);
        var lock = OperatorState.INSTANCE.getLock(linkNamespace);
        try {
            lock.lock();

            var linkSingleton = OperatorState.INSTANCE;

            var resourceLinks = new ArrayList<>(linkSingleton.getLinkResources(linkNamespace));

            var oldLinkRes = getOldLink(th2Link, resourceLinks);

            int refreshedBoxCount = 0;

            checkForDuplicateNames(th2Link.getSpec().getBoxesRelation().getRouterMq(), annotationFor(th2Link));
            checkForDuplicateNames(th2Link.getSpec().getBoxesRelation().getRouterGrpc(), annotationFor(th2Link));
            checkForDuplicateNames(th2Link.getSpec().getDictionariesRelation(), annotationFor(th2Link));

            logger.info("Updating all dependent boxes according to provided links ...");

            refreshedBoxCount = refreshBoxesIfNeeded(oldLinkRes, th2Link);
            resourceLinks.remove(th2Link);
            resourceLinks.add(th2Link);

            logger.info("{} box-definition(s) updated", refreshedBoxCount);

            linkSingleton.setLinkResources(linkNamespace, resourceLinks);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onUpdate(Th2Link oldTh2Link, Th2Link newTh2Link) {

        var linkNamespace = extractNamespace(newTh2Link);
        var lock = OperatorState.INSTANCE.getLock(linkNamespace);
        try {
            lock.lock();

            var linkSingleton = OperatorState.INSTANCE;

            var resourceLinks = new ArrayList<>(linkSingleton.getLinkResources(linkNamespace));

            var oldLinkRes = getOldLink(newTh2Link, resourceLinks);

            int refreshedBoxCount = 0;

            checkForDuplicateNames(newTh2Link.getSpec().getBoxesRelation().getRouterMq(), annotationFor(newTh2Link));
            checkForDuplicateNames(newTh2Link.getSpec().getBoxesRelation().getRouterGrpc(), annotationFor(newTh2Link));
            checkForDuplicateNames(newTh2Link.getSpec().getDictionariesRelation(), annotationFor(newTh2Link));


            logger.info("Updating all dependent boxes according to updated links ...");

            refreshedBoxCount = refreshBoxesIfNeeded(oldLinkRes, newTh2Link);

            resourceLinks.remove(newTh2Link);

            resourceLinks.add(newTh2Link);

            logger.info("{} box-definition(s) updated", refreshedBoxCount);

            linkSingleton.setLinkResources(linkNamespace, resourceLinks);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onDelete(Th2Link th2Link, boolean deletedFinalStateUnknown) {

        var linkNamespace = extractNamespace(th2Link);
        var lock = OperatorState.INSTANCE.getLock(linkNamespace);
        try {
            lock.lock();

            var linkSingleton = OperatorState.INSTANCE;

            var resourceLinks = new ArrayList<>(linkSingleton.getLinkResources(linkNamespace));

            var oldLinkRes = getOldLink(th2Link, resourceLinks);

            int refreshedBoxCount = 0;

            checkForDuplicateNames(th2Link.getSpec().getBoxesRelation().getRouterMq(), annotationFor(th2Link));
            checkForDuplicateNames(th2Link.getSpec().getBoxesRelation().getRouterGrpc(), annotationFor(th2Link));
            checkForDuplicateNames(th2Link.getSpec().getDictionariesRelation(), annotationFor(th2Link));


            logger.info("Updating all dependent boxes of destroyed links ...");

            refreshedBoxCount = refreshBoxesIfNeeded(oldLinkRes, Th2Link.newInstance());

            resourceLinks.remove(th2Link);

            //TODO: Error case not covered

            logger.info("{} box-definition(s) updated", refreshedBoxCount);

            linkSingleton.setLinkResources(linkNamespace, resourceLinks);
        } finally {
            lock.unlock();
        }
    }

    private Th2Link getOldLink(Th2Link th2Link, List<Th2Link> resourceLinks) {
        var oldLinkIndex = resourceLinks.indexOf(th2Link);
        return oldLinkIndex < 0 ? Th2Link.newInstance() : resourceLinks.get(oldLinkIndex);
    }


    private int refreshBoxesIfNeeded(Th2Link oldLinkRes, Th2Link newLinkRes) {

        var linkNamespace = extractNamespace(oldLinkRes);

        if (linkNamespace == null) {
            linkNamespace = extractNamespace(newLinkRes);
        }

        var boxesToUpdate = getBoxesToUpdate(oldLinkRes, newLinkRes);

        logger.info("{} box(es) need updating", boxesToUpdate.size());

        return DefaultWatchManager.getInstance().refreshBoxes(linkNamespace, boxesToUpdate);
    }

    private Set<String> getBoxesToUpdate(Th2Link oldLinkRes, Th2Link newLinkRes) {

        var oldBoxesLinks = oldLinkRes.getSpec().getBoxesRelation().getAllLinks();
        var newBoxesLinks = newLinkRes.getSpec().getBoxesRelation().getAllLinks();
        var fromBoxesLinks = getBoxesToUpdate(oldBoxesLinks, newBoxesLinks,
                blb -> Set.of(blb.getFrom().getBoxName(), blb.getTo().getBoxName()));
        Set<String> boxes = new HashSet<>(fromBoxesLinks);

        var oldLinks = oldLinkRes.getSpec().getDictionariesRelation();
        var newLinks = newLinkRes.getSpec().getDictionariesRelation();
        var fromDicLinks = getBoxesToUpdate(oldLinks, newLinks, dlb -> Set.of(dlb.getBox()));
        boxes.addAll(fromDicLinks);

        return boxes;
    }

    private <T extends Identifiable> Set<String> getBoxesToUpdate(List<T> oldLinks, List<T> newLinks,
                                                          Function<T, Set<String>> boxesExtractor) {

        Set<String> boxes = new HashSet<>();

        var or = new OrderedRelation<>(oldLinks, newLinks);

        for (var maxLink : or.getMaxLinks()) {
            var isLinkExist = false;
            for (var minLink : or.getMinLinks()) {
                if (minLink.getId().equals(maxLink.getId())) {
                    if (!minLink.equals(maxLink)) {
                        boxes.addAll(boxesExtractor.apply(minLink));
                        boxes.addAll(boxesExtractor.apply(maxLink));
                    }
                    isLinkExist = true;
                }
            }
            if (!isLinkExist) {
                boxes.addAll(boxesExtractor.apply(maxLink));
            }
        }

        var oldToUpdate = oldLinks.stream()
                .filter(t -> newLinks.stream().noneMatch(t1 -> t1.getId().equals(t.getId())))
                .flatMap(t -> boxesExtractor.apply(t).stream())
                .collect(Collectors.toSet());

        boxes.addAll(oldToUpdate);

        return boxes;
    }


    private static class OrderedRelation<T> {

        private List<T> maxLinks;
        private List<T> minLinks;

        public OrderedRelation(List<T> oldLinks, List<T> newLinks) {
            if (newLinks.size() >= oldLinks.size()) {
                this.maxLinks = newLinks;
                this.minLinks = oldLinks;
            } else {
                this.maxLinks = oldLinks;
                this.minLinks = newLinks;
            }
        }

        public List<T> getMaxLinks() {
            return this.maxLinks;
        }

        public List<T> getMinLinks() {
            return this.minLinks;
        }
    }

}

