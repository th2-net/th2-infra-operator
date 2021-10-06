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
import com.exactpro.th2.infraoperator.model.kubernetes.client.impl.DictionaryClient;
import com.exactpro.th2.infraoperator.spec.dictionary.Th2Dictionary;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.NonTerminalException;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.RetryableTaskQueue;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.tasks.TriggerRedeployTask;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMeta;
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

import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.RESYNC_TIME;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;

public class Th2DictionaryEventHandler implements Watcher<Th2Dictionary> {

    private static final Logger logger = LoggerFactory.getLogger(Th2DictionaryEventHandler.class);

    private static final int REDEPLOY_DELAY = 120;

    private final RetryableTaskQueue retryableTaskQueue = new RetryableTaskQueue();

    private KubernetesClient kubClient;

    private final String dictionaryAlias = "-dictionary";

    private final Map<String, String> sourceHashes = new ConcurrentHashMap<>();

    public static Th2DictionaryEventHandler newInstance(SharedInformerFactory sharedInformerFactory,
                                                        KubernetesClient kubernetesClient,
                                                        EventQueue eventQueue) {
        var res = new Th2DictionaryEventHandler();
        res.kubClient = kubernetesClient;
        SharedIndexInformer<Th2Dictionary> dictionaryInformer =
                sharedInformerFactory.sharedIndexInformerForCustomResource(
                        Th2Dictionary.class, RESYNC_TIME);

        dictionaryInformer.addEventHandler(new GenericResourceEventHandler<>(res, eventQueue));
        return res;
    }

    private Set<String> getBoundResources(Th2Dictionary dictionary) {

        Set<String> resources = new HashSet<>();
        String namespace = extractNamespace(dictionary);

        OperatorState operatorState = OperatorState.INSTANCE;

        for (var dictionaryBinding : operatorState.getDictionaryLinks(namespace)) {
            if (dictionaryBinding.getDictionary().getName().equals(extractName(dictionary))) {
                resources.add(dictionaryBinding.getBox());
            }
        }

        return resources;
    }

    @Override
    public void eventReceived(Action action, Th2Dictionary dictionary) {

        String namespace = ExtractUtils.extractNamespace(dictionary);
        String resName = ExtractUtils.extractName(dictionary);
        String resourceLabel = annotationFor(dictionary);
        String sourceHash = ExtractUtils.sourceHash(dictionary, false);
        String prevHash = sourceHashes.get(resourceLabel);

        if (action == Action.MODIFIED && prevHash != null && prevHash.equals(sourceHash)) {
            logger.info("Dictionary: \"{}\" has not been changed", resourceLabel);
            return;
        }
        Histogram.Timer processTimer = OperatorMetrics.getEventTimer(dictionary.getKind());

        try {
            logger.info("Updating all boxes with bindings to \"{}\"", resourceLabel);

            var resources = getBoundResources(dictionary);
            int items = resources.size();

            if (items == 0) {
                logger.info("No boxes needs to be updated");
            } else {
                logger.info("{} box(es) needs to be updated", items);
                DefaultWatchManager.getInstance().refreshBoxes(extractNamespace(dictionary), resources);
            }

            if (action == Action.DELETED || action == Action.ERROR) {
                kubClient.configMaps().inNamespace(namespace).withName(resName + dictionaryAlias).delete();
                sourceHashes.remove(resourceLabel);
            } else {
                kubClient.configMaps().inNamespace(namespace).createOrReplace(toConfigMap(dictionary));
                sourceHashes.put(resourceLabel, sourceHash);
            }
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
                    new DictionaryClient(kubClient), kubClient, dictionary, action, REDEPLOY_DELAY);
            retryableTaskQueue.add(triggerRedeployTask, true);

            logger.info("Task \"{}\" added to scheduler, with delay \"{}\" seconds",
                    triggerRedeployTask.getName(), REDEPLOY_DELAY);
        } catch (Exception e) {
            logger.error("Terminal Exception processing {} event for {}. Will not try to redeploy",
                    action, resourceLabel, e);
        } finally {
            processTimer.observeDuration();
        }

    }

    private ConfigMap toConfigMap(Th2Dictionary dictionary) {
        var configMapMD = new ObjectMeta();
        Map<String, String> configMapData = new HashMap<>();

        var resMD = dictionary.getMetadata();
        var resName = resMD.getName();

        configMapMD.setName(resName + dictionaryAlias);
        configMapMD.setNamespace(ExtractUtils.extractNamespace(dictionary));
        configMapMD.setLabels(resMD.getLabels());
        configMapMD.setAnnotations(resMD.getAnnotations() != null ? resMD.getAnnotations() : new HashMap<>());

        String encodedAlias = ".encoded";
        String fieldName = resName + encodedAlias;
        configMapData.put(fieldName, dictionary.getSpec().getData());

        ConfigMap configMap = new ConfigMap();
        configMap.setData(configMapData);
        configMap.setMetadata(configMapMD);
        return configMap;
    }

    @Override
    public void onClose(WatcherException cause) {
        throw new AssertionError("This method should not be called");
    }
}

