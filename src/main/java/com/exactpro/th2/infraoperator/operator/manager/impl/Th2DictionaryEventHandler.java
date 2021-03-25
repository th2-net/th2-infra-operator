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
import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.spec.dictionary.Th2Dictionary;
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.*;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.RESYNC_TIME;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;

public class Th2DictionaryEventHandler implements Watcher<Th2Dictionary> {
    private static final Logger logger = LoggerFactory.getLogger(Th2DictionaryEventHandler.class);

    //TODO remove
    private static final List<String> types = Arrays.asList("MAIN", "LEVEL1", "LEVEL2", "INCOMING", "OUTGOING");

    private MixedOperation<HelmRelease, KubernetesResourceList<HelmRelease>, Resource<HelmRelease>>
            helmReleaseClient = new DefaultKubernetesClient().customResources(HelmRelease.class);

    public static Th2DictionaryEventHandler newInstance(SharedInformerFactory sharedInformerFactory,
                                                        KubernetesClient kubernetesClient,
                                                        EventQueue eventQueue) {
        var res = new Th2DictionaryEventHandler();
        res.helmReleaseClient = kubernetesClient.customResources(HelmRelease.class);
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

        String resNamespace = ExtractUtils.extractNamespace(dictionary);
        String resName = ExtractUtils.extractName(dictionary);
        String resourceLabel = annotationFor(dictionary);
        String sourceHash = ExtractUtils.sourceHash(dictionary, false);
        String prevHash = sourceHashes.get(resourceLabel);

        if (action == Action.MODIFIED && prevHash != null && prevHash.equals(sourceHash)) {
            logger.info("Dictionary has not been changed");
            return;
        }

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
            sourceHashes.remove(resourceLabel);
            helmReleaseClient.inNamespace(resNamespace).withName(resName).delete();
        } else {
            //TODO definitely remove
            for (String type : types) {
                HelmRelease helmRelease = new HelmRelease();
                mapProperties(dictionary, helmRelease, type);
                helmReleaseClient.inNamespace(resNamespace).createOrReplace(helmRelease);
            }
            sourceHashes.put(resourceLabel, sourceHash);
        }
    }

    private void mapProperties(Th2Dictionary dictionary, HelmRelease helmRelease, String type) {
        String dataAlias = "data";

        //TODO refactor
        var helmReleaseMD = helmRelease.getMetadata();
        var resMD = dictionary.getMetadata();
        var resName = resMD.getName();

        helmReleaseMD.setName(resName + "-" + type.toLowerCase());
        helmReleaseMD.setNamespace(ExtractUtils.extractNamespace(dictionary));
        helmReleaseMD.setLabels(resMD.getLabels());
        helmReleaseMD.setAnnotations(resMD.getAnnotations());
        helmReleaseMD.setAnnotations(helmReleaseMD.getAnnotations() != null
                ? helmReleaseMD.getAnnotations() : new HashMap<>());

        //TODO take config from charts
        Map<String, Object> chartCfg = new HashMap<>(OperatorConfig.INSTANCE.getChartConfig().toMap());
        chartCfg.put("path", "./dictionary/");

        helmRelease.mergeSpecProp(CHART_PROPERTIES_ALIAS, chartCfg);
        helmRelease.mergeValue(ROOT_PROPERTIES_ALIAS, Map.of(
                COMPONENT_NAME_ALIAS, resName + "-" + type,
                dataAlias, dictionary.getSpec().getData()
        ));
    }

    @Override
    public void onClose(WatcherException cause) {
        throw new AssertionError("This method should not be called");
    }
}

