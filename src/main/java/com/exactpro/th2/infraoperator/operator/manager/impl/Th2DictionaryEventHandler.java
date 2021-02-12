package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.model.kubernetes.client.ipml.DictionaryClient;
import com.exactpro.th2.infraoperator.spec.dictionary.Th2Dictionary;
import com.exactpro.th2.infraoperator.spec.dictionary.Th2DictionaryList;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;

public class Th2DictionaryEventHandler implements WatchHandler<Th2Dictionary> {
    private static final Logger logger = LoggerFactory.getLogger(Th2DictionaryEventHandler.class);

    private DictionaryClient dictionaryClient;

    public static Th2DictionaryEventHandler newInstance (SharedInformerFactory sharedInformerFactory,
                                                         DictionaryClient dictionaryClient,
                                                         DefaultWatchManager.EventStorage<DefaultWatchManager.DispatcherEvent> eventStorage) {
        var res = new Th2DictionaryEventHandler();
        res.dictionaryClient = dictionaryClient;
        SharedIndexInformer<Th2Dictionary> dictionaryInformer = sharedInformerFactory.sharedIndexInformerForCustomResource(
                CustomResourceDefinitionContext.fromCrd(res.dictionaryClient.getCustomResourceDefinition()),
                Th2Dictionary.class,
                Th2DictionaryList.class,
                CustomResourceUtils.RESYNC_TIME);

        dictionaryInformer.addEventHandlerWithResyncPeriod(CustomResourceUtils.resourceEventHandlerFor(
                res,
                Th2Dictionary.class,
                res.dictionaryClient.getCustomResourceDefinition(),
                eventStorage),
                0);
        return res;
    }

    private Set<String> getLinkedResources(Th2Dictionary dictionary) {
        Set<String> resources = new HashSet<>();

        var lSingleton = OperatorState.INSTANCE;

        for (var linkRes : lSingleton.getLinkResources(extractNamespace(dictionary))) {
            for (var dicLink : linkRes.getSpec().getDictionariesRelation()) {
                if (dicLink.getDictionary().getName().contains(extractName(dictionary))) {
                    resources.add(dicLink.getBox());
                }
            }
        }

        return resources;
    }

    private void handleEvent (Watcher.Action action, Th2Dictionary dictionary) {
        String resourceLabel = annotationFor(dictionary);
        logger.info("Updating all boxes that contains dictionary \"{}\"", resourceLabel);

        var linkedResources = getLinkedResources(dictionary);

        logger.info("{} box(es) need updating", linkedResources.size());

        var refreshedBoxCount = DefaultWatchManager.getInstance().refreshBoxes(extractNamespace(dictionary), linkedResources);

        logger.info("{} box-definition(s) updated", refreshedBoxCount);
    }

    @Override
    public void onAdd(Th2Dictionary dictionary) {
        handleEvent(Watcher.Action.ADDED, dictionary);
    }

    @Override
    public void onUpdate(Th2Dictionary oldDictionary, Th2Dictionary newDictionary) {
        handleEvent(Watcher.Action.MODIFIED, newDictionary);
    }

    @Override
    public void onDelete(Th2Dictionary dictionary, boolean deletedFinalStateUnknown) {
        handleEvent(Watcher.Action.DELETED, dictionary);
    }

    @Override
    public void eventReceived(Action action, Th2Dictionary resource) {
        handleEvent(action, resource);
    }

    @Override
    public void onClose(WatcherException cause) {

    }
}

