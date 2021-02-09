package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.configuration.RabbitMQConfig;
import com.exactpro.th2.infraoperator.model.kubernetes.configmaps.ConfigMaps;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.mq.impl.RabbitMQContext;
import com.exactpro.th2.infraoperator.util.Strings;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static com.exactpro.th2.infraoperator.configuration.RabbitMQConfig.CONFIG_MAP_RABBITMQ_PROP_NAME;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.JsonUtils.JSON_READER;

public class ConfigMapEventHandler implements ResourceEventHandler<ConfigMap> {
    private static final Logger logger = LoggerFactory.getLogger(ConfigMapEventHandler.class);

    protected KubernetesClient client;

    public ConfigMapEventHandler (KubernetesClient client) {
        this.client = client;
    }

    private void processEvent (Watcher.Action action, ConfigMap configMap) {
        String resourceLabel = annotationFor(configMap);
        String namespace = configMap.getMetadata().getNamespace();
        String configMapName = configMap.getMetadata().getName();

        if (!(configMapName.equals(OperatorConfig.INSTANCE.getRabbitMQConfigMapName())))
            return;
        try {
            logger.info("Processing {} event for \"{}\"", action, resourceLabel);

            if (configMapName.equals(OperatorConfig.INSTANCE.getRabbitMQConfigMapName())) {
                var lock = OperatorState.INSTANCE.getLock(namespace);
                try {
                    lock.lock();

                    OperatorConfig opConfig = OperatorConfig.INSTANCE;
                    ConfigMaps configMaps = ConfigMaps.INSTANCE;
                    RabbitMQConfig rabbitMQConfig = configMaps.getRabbitMQConfig4Namespace(namespace);

                    String configContent = configMap.getData().get(CONFIG_MAP_RABBITMQ_PROP_NAME);
                    if (Strings.isNullOrEmpty(configContent)) {
                        logger.error("Key \"{}\" not found in \"{}\"", CONFIG_MAP_RABBITMQ_PROP_NAME,
                                resourceLabel);
                        return;
                    }

                    RabbitMQConfig newRabbitMQConfig = JSON_READER.readValue(configContent, RabbitMQConfig.class);
                    newRabbitMQConfig.setPassword(DefaultWatchManager.getInstance().readRabbitMQPasswordForSchema(client, namespace,
                            opConfig.getSchemaSecrets().getRabbitMQ()));

                    if (!Objects.equals(rabbitMQConfig, newRabbitMQConfig)) {
                        configMaps.setRabbitMQConfig4Namespace(namespace, newRabbitMQConfig);
                        RabbitMQContext.createVHostIfAbsent(namespace);
                        logger.info("RabbitMQ ConfigMap has been updated in namespace \"{}\". Updating all boxes",
                                namespace);
                        int refreshedBoxesCount = DefaultWatchManager.getInstance().refreshBoxes(namespace);
                        logger.info("{} box-definition(s) have been updated", refreshedBoxesCount);
                    } else
                        logger.info("RabbitMQ ConfigMap data hasn't changed");
                } finally {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            logger.error("Exception processing {} event for \"{}\"", action, resourceLabel, e);
        }
    }

    @Override
    public void onAdd(ConfigMap configMap) {
        processEvent(Watcher.Action.ADDED, configMap);
    }

    @Override
    public void onUpdate(ConfigMap oldConfigMap, ConfigMap newConfigMap) {
        processEvent(Watcher.Action.MODIFIED, newConfigMap);
    }

    @Override
    public void onDelete(ConfigMap configMap, boolean deletedFinalStateUnknown) {

    }
}

