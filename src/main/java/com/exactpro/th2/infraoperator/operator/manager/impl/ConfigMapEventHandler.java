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
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import com.exactpro.th2.infraoperator.util.Strings;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.prometheus.client.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Objects;

import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.JsonUtils.JSON_READER;

public class ConfigMapEventHandler implements Watcher<ConfigMap> {
    public static final String SECRET_TYPE_OPAQUE = "Opaque";

    public static final String LOGGING_CM_NAME = "logging-config";

    public static final String MQ_ROUTER_CM_NAME = "mq-router";

    public static final String GRPC_ROUTER_CM_NAME = "grpc-router";

    public static final String CRADLE_MANAGER_CM_NAME = "cradle-manager";

    private static final Logger logger = LoggerFactory.getLogger(ConfigMapEventHandler.class);

    private KubernetesClient client;

    public KubernetesClient getClient() {
        return client;
    }

    public static ConfigMapEventHandler newInstance(SharedInformerFactory sharedInformerFactory,
                                                    KubernetesClient client,
                                                    EventQueue eventQueue) {
        var res = new ConfigMapEventHandler(client);
        res.client = client;

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
            Histogram.Timer processTimer = OperatorMetrics.getEventTimer(resource.getKind());
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
                        configMaps.setRabbitMQConfig4Namespace(namespace, newRabbitMQConfig);
                        RabbitMQContext.createVHostIfAbsent(namespace);
                        logger.info("RabbitMQ ConfigMap has been updated in namespace \"{}\". Updating all boxes",
                                namespace);
                        int refreshedBoxesCount = DefaultWatchManager.getInstance().refreshBoxes(namespace);
                        logger.info("{} box-definition(s) have been updated", refreshedBoxesCount);
                    } else {
                        logger.info("RabbitMQ ConfigMap data hasn't changed");
                    }
                } finally {
                    processTimer.observeDuration();
                    lock.unlock();
                }
            } catch (Exception e) {
                logger.error("Exception processing {} event for \"{}\"", action, resourceLabel, e);
            }
        } else if (configMapName.equals(LOGGING_CM_NAME)) {
            updateChecksum(action, namespace, resource, LOGGING_CM_NAME, resourceLabel);
        } else if (configMapName.equals(MQ_ROUTER_CM_NAME)) {
            updateChecksum(action, namespace, resource, MQ_ROUTER_CM_NAME, resourceLabel);
        } else if (configMapName.equals(GRPC_ROUTER_CM_NAME)) {
            updateChecksum(action, namespace, resource, GRPC_ROUTER_CM_NAME, resourceLabel);
        } else if (configMapName.equals(CRADLE_MANAGER_CM_NAME)) {
            updateChecksum(action, namespace, resource, CRADLE_MANAGER_CM_NAME, resourceLabel);
        }

    }

    private void updateChecksum(Action action, String namespace, ConfigMap resource, String key, String resourceLabel) {
        Histogram.Timer processTimer = OperatorMetrics.getEventTimer(resource.getKind());
        try {
            logger.info("Processing {} event for \"{}\"", action, resourceLabel);

            var lock = OperatorState.INSTANCE.getLock(namespace);
            try {
                lock.lock();
                String prevHash = OperatorState.INSTANCE.getConfigChecksum(namespace, key);
                String currentHash = ExtractUtils.sourceHash(resource, false);
                if (!currentHash.equals(prevHash)) {
                    OperatorState.INSTANCE.putConfigChecksum(namespace, key, currentHash);
                    logger.info("\"{}\" has been updated. Updating all boxes", resourceLabel);
                    int refreshedBoxesCount = DefaultWatchManager.getInstance().refreshBoxes(namespace);
                    logger.info("{} box-definition(s) have been updated", refreshedBoxesCount);
                }
            } finally {
                processTimer.observeDuration();
                lock.unlock();
            }
        } catch (Exception e) {
            logger.error("Exception processing {} event for \"{}\"", action, resourceLabel, e);
        }
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
}
