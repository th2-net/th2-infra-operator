package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.shared.Identifiable;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import lombok.Getter;
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

    private <T extends Identifiable> void checkForSameName(List<T> links, String annotation) {
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

            var oldLinkRes = DefaultWatchManager.getInstance().getOldLink(th2Link, resourceLinks);

            int refreshedBoxCount = 0;

            checkForSameName(th2Link.getSpec().getBoxesRelation().getRouterMq(), annotationFor(th2Link));
            checkForSameName(th2Link.getSpec().getBoxesRelation().getRouterGrpc(), annotationFor(th2Link));
            checkForSameName(th2Link.getSpec().getDictionariesRelation(), annotationFor(th2Link));

            logger.info("Updating all dependent boxes according to provided links ...");

            refreshedBoxCount = DefaultWatchManager.getInstance().refreshBoxesIfNeeded(oldLinkRes, th2Link);
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

            var oldLinkRes = DefaultWatchManager.getInstance().getOldLink(newTh2Link, resourceLinks);

            int refreshedBoxCount = 0;

            checkForSameName(newTh2Link.getSpec().getBoxesRelation().getRouterMq(), annotationFor(newTh2Link));
            checkForSameName(newTh2Link.getSpec().getBoxesRelation().getRouterGrpc(), annotationFor(newTh2Link));
            checkForSameName(newTh2Link.getSpec().getDictionariesRelation(), annotationFor(newTh2Link));


            logger.info("Updating all dependent boxes according to updated links ...");

            refreshedBoxCount = DefaultWatchManager.getInstance().refreshBoxesIfNeeded(oldLinkRes, newTh2Link);

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

            var oldLinkRes = DefaultWatchManager.getInstance().getOldLink(th2Link, resourceLinks);

            int refreshedBoxCount = 0;

            checkForSameName(th2Link.getSpec().getBoxesRelation().getRouterMq(), annotationFor(th2Link));
            checkForSameName(th2Link.getSpec().getBoxesRelation().getRouterGrpc(), annotationFor(th2Link));
            checkForSameName(th2Link.getSpec().getDictionariesRelation(), annotationFor(th2Link));


            logger.info("Updating all dependent boxes of destroyed links ...");

            refreshedBoxCount = DefaultWatchManager.getInstance().refreshBoxesIfNeeded(oldLinkRes, Th2Link.newInstance());

            resourceLinks.remove(th2Link);

            //TODO: Error case not covered

            logger.info("{} box-definition(s) updated", refreshedBoxCount);

            linkSingleton.setLinkResources(linkNamespace, resourceLinks);
        } finally {
            lock.unlock();
        }
    }
}

