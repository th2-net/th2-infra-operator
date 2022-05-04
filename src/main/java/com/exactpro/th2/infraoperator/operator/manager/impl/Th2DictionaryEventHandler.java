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
import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.MultiDictionaryEntity;
import com.exactpro.th2.infraoperator.model.kubernetes.client.impl.DictionaryClient;
import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.DictionaryEntity;
import com.exactpro.th2.infraoperator.spec.dictionary.Th2Dictionary;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.NonTerminalException;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.RetryableTaskQueue;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.tasks.TriggerRedeployTask;
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.prometheus.client.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.*;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.RESYNC_TIME;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;
import static com.exactpro.th2.infraoperator.util.HelmReleaseUtils.extractMultiDictionariesConfig;
import static com.exactpro.th2.infraoperator.util.HelmReleaseUtils.extractOldDictionariesConfig;

public class Th2DictionaryEventHandler implements Watcher<Th2Dictionary> {

    private static final Logger logger = LoggerFactory.getLogger(Th2DictionaryEventHandler.class);

    private static final int REDEPLOY_DELAY = 120;

    private final RetryableTaskQueue retryableTaskQueue = new RetryableTaskQueue();

    private KubernetesClient kubClient;

    private MixedOperation<HelmRelease, KubernetesResourceList<HelmRelease>, Resource<HelmRelease>> helmReleaseClient;

    private final String dictionaryAlias = "-dictionary";

    private final Map<String, String> sourceHashes = new ConcurrentHashMap<>();

    public static Th2DictionaryEventHandler newInstance(SharedInformerFactory sharedInformerFactory,
                                                        KubernetesClient kubernetesClient,
                                                        EventQueue eventQueue) {
        var res = new Th2DictionaryEventHandler();
        res.kubClient = kubernetesClient;
        res.helmReleaseClient = kubernetesClient.resources(HelmRelease.class);
        SharedIndexInformer<Th2Dictionary> dictionaryInformer =
                sharedInformerFactory.sharedIndexInformerFor(
                        Th2Dictionary.class, RESYNC_TIME);

        dictionaryInformer.addEventHandler(new GenericResourceEventHandler<>(res, eventQueue));
        return res;
    }

    @Override
    public void eventReceived(Action action, Th2Dictionary dictionary) {
        Histogram.Timer processTimer = OperatorMetrics.getDictionaryEventTimer(dictionary);
        try {

            switch (action) {
                case ADDED:
                    processAdded(dictionary);
                    break;
                case MODIFIED:
                    processModified(dictionary);
                    break;
                case ERROR:
                case DELETED:
                    processDeleted(dictionary);
                    break;
            }
        } catch (NonTerminalException e) {
            String resourceLabel = annotationFor(dictionary);
            String namespace = ExtractUtils.extractNamespace(dictionary);

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
            String resourceLabel = annotationFor(dictionary);
            logger.error("Terminal Exception processing {} event for {}. Will not try to redeploy",
                    action, resourceLabel, e);
        } finally {
            //observe event processing time for only operator
            processTimer.observeDuration();
        }
    }

    private void processAdded(Th2Dictionary dictionary) {
        String namespace = ExtractUtils.extractNamespace(dictionary);
        String resourceLabel = annotationFor(dictionary);
        String newChecksum = ExtractUtils.fullSourceHash(dictionary);

        //create or replace corresponding config map from Kubernetes
        logger.debug("Creating config map for: \"{}\"", resourceLabel);
        kubClient.configMaps().inNamespace(namespace).createOrReplace(toConfigMap(dictionary));
        logger.debug("Created config map for: \"{}\"", resourceLabel);
        sourceHashes.put(resourceLabel, newChecksum);
    }

    private void processModified(Th2Dictionary dictionary) {
        String dictionaryName = ExtractUtils.extractName(dictionary);
        String namespace = ExtractUtils.extractNamespace(dictionary);
        String resourceLabel = annotationFor(dictionary);
        String newChecksum = ExtractUtils.fullSourceHash(dictionary);
        String oldChecksum = sourceHashes.get(resourceLabel);

        if (oldChecksum != null && oldChecksum.equals(newChecksum)) {
            logger.info("Dictionary: \"{}\" has not been changed", resourceLabel);
            return;
        }

        //update corresponding config map from Kubernetes
        logger.debug("Updating config map for: \"{}\"", resourceLabel);
        kubClient.configMaps().inNamespace(namespace).createOrReplace(toConfigMap(dictionary));
        logger.debug("Updated config map for: \"{}\"", resourceLabel);
        sourceHashes.put(resourceLabel, newChecksum);

        logger.info("Checking bindings for \"{}\"", resourceLabel);

        var linkedResources = getLinkedResources(dictionary);
        int items = linkedResources.size();

        if (items == 0) {
            logger.info("No boxes needs to be updated");
        } else {
            logger.info("{} box(es) needs to be updated", items);
            updateLinkedResources(dictionaryName, namespace, newChecksum, linkedResources);
        }
    }

    private void processDeleted(Th2Dictionary dictionary) {
        String dictionaryName = ExtractUtils.extractName(dictionary);
        String namespace = ExtractUtils.extractNamespace(dictionary);
        String resourceLabel = annotationFor(dictionary);

        //delete corresponding config map from Kubernetes
        logger.debug("Deleting config map for: \"{}\"", resourceLabel);
        kubClient.configMaps().inNamespace(namespace).withName(dictionaryName + dictionaryAlias).delete();
        sourceHashes.remove(resourceLabel);
        logger.debug("Deleted config map for: \"{}\"", resourceLabel);
    }

    private Set<String> getLinkedResources(Th2Dictionary dictionary) {

        Set<String> resources = new HashSet<>();
        String namespace = extractNamespace(dictionary);

        OperatorState operatorState = OperatorState.INSTANCE;

        String dictionaryName = extractName(dictionary);
        for (var dictionaryBinding : operatorState.getDictionaryLinks(namespace)) {
            if (dictionaryBinding.getDictionary().getName().equals(dictionaryName)) {
                resources.add(dictionaryBinding.getBox());
            }
        }
        for (var dictionaryBinding : operatorState.getMultiDictionaryLinks(namespace)) {
            if (dictionaryBinding.getDictionaries().stream().anyMatch(dict -> dict.getName().equals(dictionaryName))) {
                resources.add(dictionaryBinding.getBox());
            }
        }

        return resources;
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

    private void updateLinkedResources(String dictionaryName, String namespace,
                                       String checksum, Set<String> linkedResources) {

        Namespace namespaceObj = kubClient.namespaces().withName(namespace).get();
        if (namespaceObj == null || !namespaceObj.getStatus().getPhase().equals("Active")) {
            logger.info("Namespace \"{}\" deleted or not active, cancelling", namespace);
            return;
        }
        for (var linkedResourceName : linkedResources) {
            logger.debug("Checking linked resource: '{}.{}'", namespace, linkedResourceName);

            var hr = OperatorState.INSTANCE.getHelmReleaseFromCache(linkedResourceName, namespace);
            if (hr == null) {
                logger.info("HelmRelease of '{}.{}' resource not found in cache", namespace, linkedResourceName);
                continue;
            } else {
                logger.debug("Found HelmRelease \"{}\"", CustomResourceUtils.annotationFor(hr));
            }

            Collection<DictionaryEntity> dictionaryConfig = extractOldDictionariesConfig(hr);
            if (dictionaryConfig != null) {
                for (var entity : dictionaryConfig) {
                    if (entity.getName().equals(dictionaryName)) {
                        entity.updateChecksum(checksum);
                    }
                }
                hr.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                        Map.of(DICTIONARIES_ALIAS, dictionaryConfig));
            }

            List<MultiDictionaryEntity> multiDictionaryConfig = extractMultiDictionariesConfig(hr);
            if (multiDictionaryConfig != null) {
                for (var entity : multiDictionaryConfig) {
                    if (entity.getName().equals(dictionaryName)) {
                        entity.updateChecksum(checksum);
                    }
                }
                hr.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                        Map.of(MULTI_DICTIONARIES_ALIAS, multiDictionaryConfig));
            }
            logger.debug("Updating \"{}\"", CustomResourceUtils.annotationFor(hr));
            createKubObj(namespace, hr);
            logger.debug("Updated \"{}\"", CustomResourceUtils.annotationFor(hr));
        }
    }

    protected void createKubObj(String namespace, HelmRelease helmRelease) {
        helmReleaseClient.inNamespace(namespace).createOrReplace(helmRelease);
        OperatorState.INSTANCE.putHelmReleaseInCache(helmRelease, namespace);
    }

    @Override
    public void onClose(WatcherException cause) {
        throw new AssertionError("This method should not be called");
    }
}

