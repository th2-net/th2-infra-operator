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
import com.exactpro.th2.infraoperator.configuration.RabbitMQConfig;
import com.exactpro.th2.infraoperator.metrics.OperatorMetrics;
import com.exactpro.th2.infraoperator.model.kubernetes.configmaps.ConfigMaps;
import com.exactpro.th2.infraoperator.spec.Th2Spec;
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import com.exactpro.th2.infraoperator.util.HelmReleaseUtils;
import com.exactpro.th2.infraoperator.util.Strings;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.CustomResource;
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

import static com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.*;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.JsonUtils.JSON_READER;

public class ConfigMapEventHandler implements Watcher<ConfigMap> {
    public static final String SECRET_TYPE_OPAQUE = "Opaque";

    public static final String LOGGING_CM_NAME = "logging-config";

    public static final String MQ_ROUTER_CM_NAME = "mq-router";

    public static final String GRPC_ROUTER_CM_NAME = "grpc-router";

    public static final String CRADLE_MANAGER_CM_NAME = "cradle-manager";

    private static final String MQ_ROUTER_FILE_NAME = "mq_router.json";

    private static final String GRPC_ROUTER_FILE_NAME = "grpc_router.json";

    private static final String CRADLE_MANAGER_FILE_NAME = "cradle_manager.json";

    private static final Logger logger = LoggerFactory.getLogger(ConfigMapEventHandler.class);

    private KubernetesClient client;

    private MixedOperation<HelmRelease, KubernetesResourceList<HelmRelease>, Resource<HelmRelease>> helmReleaseClient;

    public KubernetesClient getClient() {
        return client;
    }

    public static ConfigMapEventHandler newInstance(SharedInformerFactory sharedInformerFactory,
                                                    KubernetesClient client,
                                                    EventQueue eventQueue) {
        var res = new ConfigMapEventHandler(client);
        res.client = client;
        res.helmReleaseClient = client.resources(HelmRelease.class);

        SharedIndexInformer<ConfigMap> configMapInformer = sharedInformerFactory.sharedIndexInformerFor(
                ConfigMap.class,
                CustomResourceUtils.RESYNC_TIME);

        configMapInformer.addEventHandler(new GenericResourceEventHandler<>(res, eventQueue));
        return res;
    }

    private ConfigMapEventHandler(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public void eventReceived(Action action, ConfigMap resource) {

        String resourceLabel = annotationFor(resource);
        String namespace = resource.getMetadata().getNamespace();
        String configMapName = resource.getMetadata().getName();

        if (configMapName.equals(OperatorConfig.INSTANCE.getRabbitMQConfigMapName())) {
            try {
                logger.info("Processing {} event for \"{}\"", action, resourceLabel);
                var lock = OperatorState.INSTANCE.getLock(namespace);
                try {
                    lock.lock();

                    OperatorConfig opConfig = OperatorConfig.INSTANCE;
                    ConfigMaps configMaps = ConfigMaps.INSTANCE;
                    RabbitMQConfig rabbitMQConfig = configMaps.getRabbitMQConfig4Namespace(namespace);

                    String configContent = resource.getData().get(RabbitMQConfig.CONFIG_MAP_RABBITMQ_PROP_NAME);
                    if (Strings.isNullOrEmpty(configContent)) {
                        logger.error("Key \"{}\" not found in \"{}\"", RabbitMQConfig.CONFIG_MAP_RABBITMQ_PROP_NAME,
                                resourceLabel);
                        return;
                    }

                    RabbitMQConfig newRabbitMQConfig = JSON_READER.readValue(configContent, RabbitMQConfig.class);
                    newRabbitMQConfig.setPassword(readRabbitMQPasswordForSchema(namespace,
                            opConfig.getSchemaSecrets().getRabbitMQ()));

                    if (!Objects.equals(rabbitMQConfig, newRabbitMQConfig)) {
                        Histogram.Timer processTimer = OperatorMetrics.getConfigMapEventTimer(resource);
                        configMaps.setRabbitMQConfig4Namespace(namespace, newRabbitMQConfig);
                        RabbitMQContext.createVHostIfAbsent(namespace);
                        logger.info("RabbitMQ ConfigMap has been updated in namespace \"{}\". Updating all boxes",
                                namespace);
                        DefaultWatchManager.getInstance().refreshBoxes(namespace);
                        logger.info("box-definition(s) have been updated");
                        processTimer.observeDuration();
                    } else {
                        logger.info("RabbitMQ ConfigMap data hasn't changed");
                    }
                } finally {
                    lock.unlock();
                }
            } catch (Exception e) {
                logger.error("Exception processing {} event for \"{}\"", action, resourceLabel, e);
            }
        } else if (configMapName.equals(LOGGING_CM_NAME)) {
            updateLoggingConfigMap(action, namespace, resource, resourceLabel);
        } else if (configMapName.equals(MQ_ROUTER_CM_NAME)) {
            updateConfigMap(action, namespace, resource, MQ_ROUTER_ALIAS, MQ_ROUTER_FILE_NAME, resourceLabel);
        } else if (configMapName.equals(GRPC_ROUTER_CM_NAME)) {
            updateConfigMap(action, namespace, resource, GRPC_ROUTER_ALIAS, GRPC_ROUTER_FILE_NAME, resourceLabel);
        } else if (configMapName.equals(CRADLE_MANAGER_CM_NAME)) {
            updateConfigMap(action, namespace, resource, CRADLE_MGR_ALIAS, CRADLE_MANAGER_FILE_NAME, resourceLabel);
        }

    }

    private void updateLoggingConfigMap(Action action, String namespace, ConfigMap resource, String resourceLabel) {
        try {
            logger.info("Processing {} event for \"{}\"", action, resourceLabel);
            if (action == Action.DELETED) {
                logger.error("DELETED action is not supported for \"{}\". ", resourceLabel);
                return;
            }
            if (action == Action.ERROR) {
                logger.error("Received ERROR action for \"{}\" Canceling update", resourceLabel);
                return;
            }
            var lock = OperatorState.INSTANCE.getLock(namespace);
            try {
                lock.lock();
                String oldChecksum = OperatorState.INSTANCE.getConfigChecksum(namespace, LOGGING_ALIAS);
                String newChecksum = ExtractUtils.sourceHash(resource, false);
                if (!newChecksum.equals(oldChecksum)) {
                    Histogram.Timer processTimer = OperatorMetrics.getConfigMapEventTimer(resource);
                    OperatorState.INSTANCE.putConfigChecksum(namespace, LOGGING_ALIAS, newChecksum);
                    logger.info("\"{}\" has been updated. Updating all boxes", resourceLabel);
                    int refreshedBoxesCount = updateResourceChecksums(namespace, newChecksum, LOGGING_ALIAS);
                    logger.info("{} HelmRelease(s) have been updated", refreshedBoxesCount);
                    processTimer.observeDuration();
                }
            } finally {
                lock.unlock();
            }
        } catch (Exception e) {
            logger.error("Exception processing {} event for \"{}\"", action, resourceLabel, e);
        }
    }

    private int updateResourceChecksums(String namespace, String checksum, String key) {
        Collection<HelmRelease> helmReleases = OperatorState.INSTANCE.getAllHelmReleases(namespace);
        for (var hr : helmReleases) {
            Map<String, Object> config = HelmReleaseUtils.extractConfigSection(hr, key);
            config.put(CHECKSUM_ALIAS, checksum);
            hr.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                    Map.of(key, config));

            logger.debug("Updating \"{}\" resource", CustomResourceUtils.annotationFor(hr));
            createKubObj(namespace, hr);
            logger.debug("\"{}\" Updated", CustomResourceUtils.annotationFor(hr));
        }
        return helmReleases.size();
    }

    private void updateConfigMap(Action action, String namespace, ConfigMap resource, String key, String dataFileName,
                                 String resourceLabel) {
        try {
            logger.info("Processing {} event for \"{}\"", action, resourceLabel);
            if (action == Action.DELETED) {
                logger.error("DELETED action is not supported for \"{}\". ", resourceLabel);
                return;
            }
            if (action == Action.ERROR) {
                logger.error("Received ERROR action for \"{}\" Canceling update", resourceLabel);
                return;
            }
            var lock = OperatorState.INSTANCE.getLock(namespace);
            try {
                lock.lock();
                String oldChecksum = OperatorState.INSTANCE.getConfigChecksum(namespace, key);
                String newChecksum = ExtractUtils.sourceHash(resource, false);
                if (!newChecksum.equals(oldChecksum)) {
                    Histogram.Timer processTimer = OperatorMetrics.getConfigMapEventTimer(resource);
                    OperatorState.INSTANCE.putConfigChecksum(namespace, key, newChecksum);
                    String cmData = resource.getData().get(dataFileName);
                    OperatorState.INSTANCE.putConfigData(namespace, key, cmData);
                    logger.info("\"{}\" has been updated. Updating all boxes", resourceLabel);
                    int refreshedBoxesCount = updateResourceChecksumAndData(namespace, newChecksum, cmData, key);
                    logger.info("{} HelmRelease(s) have been updated", refreshedBoxesCount);
                    processTimer.observeDuration();
                }
            } finally {
                lock.unlock();
            }
        } catch (Exception e) {
            logger.error("Exception processing {} event for \"{}\"", action, resourceLabel, e);
        }
    }

    private int updateResourceChecksumAndData(String namespace, String checksum, String cmData, String key)
            throws JsonProcessingException {
        Collection<HelmRelease> helmReleases = OperatorState.INSTANCE.getAllHelmReleases(namespace);
        for (var hr : helmReleases) {
            Map<String, Object> config = HelmReleaseUtils.extractConfigSection(hr, key);
            CustomResource cr = (CustomResource) OperatorState.INSTANCE.getResourceFromCache(
                    HelmReleaseUtils.extractComponentName(hr),
                    namespace
            );
            Map<String, Object> configInCR = getConfigFromCR(cr, key);
            if (configInCR != null) {
                config.put(CONFIG_ALIAS, mergeConfigs(cmData, configInCR));
            }
            config.put(CHECKSUM_ALIAS, checksum);
            hr.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                    Map.of(key, config));

            logger.debug("Updating \"{}\" resource", CustomResourceUtils.annotationFor(hr));
            createKubObj(namespace, hr);
            logger.debug("\"{}\" Updated", CustomResourceUtils.annotationFor(hr));
        }
        return helmReleases.size();
    }

    protected void createKubObj(String namespace, HelmRelease helmRelease) {
        helmReleaseClient.inNamespace(namespace).createOrReplace(helmRelease);
        OperatorState.INSTANCE.putHelmReleaseInCache(helmRelease, namespace);
    }

    private Map<String, Object> getConfigFromCR(CustomResource customResource, String key) {
        Th2Spec spec = (Th2Spec) customResource.getSpec();
        switch (key) {
            case MQ_ROUTER_ALIAS:
                return spec.getMqRouter();
            case GRPC_ROUTER_ALIAS:
                return spec.getGrpcRouter();
            case CRADLE_MGR_ALIAS:
                return spec.getCradleManager();
        }
        return null;
    }

    @Override
    public void onClose(WatcherException cause) {
        throw new AssertionError("This method should not be called");
    }

    private String readRabbitMQPasswordForSchema(String namespace, String secretName) throws Exception {

        Secret secret = client.secrets().inNamespace(namespace).withName(secretName).get();
        if (secret == null) {
            throw new Exception(String.format("Secret not found \"%s\"",
                    annotationFor(namespace, "Secret", secretName)));
        }
        if (secret.getData() == null) {
            throw new Exception(String.format("Invalid secret \"%s\". No data", annotationFor(secret)));
        }

        String password = secret.getData().get(OperatorConfig.RABBITMQ_SECRET_PASSWORD_KEY);
        if (password == null) {
            throw new Exception(String.format("Invalid secret \"%s\". No password was found with key \"%s\""
                    , annotationFor(secret), OperatorConfig.RABBITMQ_SECRET_PASSWORD_KEY));
        }
        if (secret.getType().equals(SECRET_TYPE_OPAQUE)) {
            password = new String(Base64.getDecoder().decode(password.getBytes()));
        }
        return password;
    }

    public static Map<String, Object> mergeConfigs(String initialDataStr,
                                                   Map<String, Object> newData) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> defaults = objectMapper.readValue(initialDataStr, new TypeReference<>() {
        });
        ObjectReader updater = objectMapper.readerForUpdating(defaults);
        String newDataStr = objectMapper.writeValueAsString(newData);
        return objectMapper.convertValue(updater.readValue(newDataStr), new TypeReference<>() {
        });
    }

}
