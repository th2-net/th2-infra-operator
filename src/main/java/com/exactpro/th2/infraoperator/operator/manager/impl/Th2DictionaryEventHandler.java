package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.model.kubernetes.client.impl.DictionaryClient;
import com.exactpro.th2.infraoperator.spec.dictionary.Th2Dictionary;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.RESYNC_TIME;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;

public class Th2DictionaryEventHandler implements Watcher<Th2Dictionary> {
    private static final Logger logger = LoggerFactory.getLogger(Th2DictionaryEventHandler.class);

    private DictionaryClient dictionaryClient;

    public static Th2DictionaryEventHandler newInstance (SharedInformerFactory sharedInformerFactory,
                                                         DictionaryClient dictionaryClient,
                                                         EventQueue eventQueue) {
        var res = new Th2DictionaryEventHandler();
        res.dictionaryClient = dictionaryClient;
        SharedIndexInformer<Th2Dictionary> dictionaryInformer = sharedInformerFactory.sharedIndexInformerForCustomResource(
                Th2Dictionary.class,
                RESYNC_TIME);

        dictionaryInformer.addEventHandler(CustomResourceUtils.resourceEventHandlerFor(
                res,
                Th2Dictionary.class,
                res.dictionaryClient.getCustomResourceDefinition(),
                eventQueue));
        return res;
    }


    private Set<String> getBoundResources(Th2Dictionary dictionary) {

        Set<String> resources = new HashSet<>();
        String namespace = extractNamespace(dictionary);

        OperatorState operatorState = OperatorState.INSTANCE;

        for (var th2link : operatorState.getLinkResources(namespace)) {
            for (var dictionaryBinding : th2link.getSpec().getDictionariesRelation()) {
                if (dictionaryBinding.getDictionary().getName().equals(extractName(dictionary))) {
                    resources.add(dictionaryBinding.getBox());
                }
            }
        }

        return resources;
    }


    private Map<String, String> sourceHashes = new ConcurrentHashMap<>();

    @Override
    public void eventReceived(Action action, Th2Dictionary dictionary) {

        String resourceLabel = annotationFor(dictionary);
        String sourceHash = ExtractUtils.sourceHash(dictionary, false);
        String prevHash = sourceHashes.get(resourceLabel);

        if (prevHash != null && prevHash.equals(sourceHash)) {
            logger.info("Dictionary has not been changed");
            return;
        }

        logger.info("Updating all boxes with bindings to \"{}\"", resourceLabel);

        var resources = getBoundResources(dictionary);
        int items = resources.size();

        if (items == 0)
            logger.info("No boxes needs to be updated");
        else {
            logger.info("{} box(es) needs to be updated", items);
            DefaultWatchManager.getInstance().refreshBoxes(extractNamespace(dictionary), resources);
        }

        if (action == Watcher.Action.DELETED || action == Watcher.Action.ERROR)
            sourceHashes.remove(resourceLabel);
        else
            sourceHashes.put(resourceLabel, sourceHash);
    }

    @Override
    public void onClose(WatcherException cause) {
        throw new AssertionError("This method should not be called");
    }
}

