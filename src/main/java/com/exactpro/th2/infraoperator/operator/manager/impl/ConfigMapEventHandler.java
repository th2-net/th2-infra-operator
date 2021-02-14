package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.configuration.RabbitMQConfig;
import com.exactpro.th2.infraoperator.model.kubernetes.configmaps.ConfigMaps;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.mq.impl.RabbitMQContext;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.Strings;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Objects;

import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.JsonUtils.JSON_READER;

public class ConfigMapEventHandler implements WatchHandler<ConfigMap>{
    public static final String SECRET_TYPE_OPAQUE = "Opaque";

    private static final Logger logger = LoggerFactory.getLogger(ConfigMapEventHandler.class);
    private KubernetesClient client;

    public static ConfigMapEventHandler newInstance(SharedInformerFactory sharedInformerFactory,
                                                    KubernetesClient client,
                                                    DefaultWatchManager.EventQueue<DefaultWatchManager.DispatcherEvent> eventQueue) {
        SharedIndexInformer<ConfigMap> configMapInformer = sharedInformerFactory.sharedIndexInformerFor(
                ConfigMap.class,
                ConfigMapList.class,
                CustomResourceUtils.RESYNC_TIME);

        var res = new ConfigMapEventHandler(client);
        configMapInformer.addEventHandlerWithResyncPeriod(new GenericResourceEventHandler<>(res, eventQueue), 0);
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


    private String readRabbitMQPasswordForSchema(String namespace, String secretName) throws Exception {

        Secret secret = client.secrets().inNamespace(namespace).withName(secretName).get();
        if (secret == null)
            throw new Exception(String.format("Secret not found \"%s\"",
                    annotationFor(namespace, "Secret", secretName)));
        if (secret.getData() == null)
            throw new Exception(String.format("Invalid secret \"%s\". No data",
                    annotationFor(secret)));

        String password = secret.getData().get(OperatorConfig.RABBITMQ_SECRET_PASSWORD_KEY);
        if (password == null)
            throw new Exception(String.format("Invalid secret \"%s\". No password was found with key \"%s\""
                    , annotationFor(secret)
                    , OperatorConfig.RABBITMQ_SECRET_PASSWORD_KEY));

        if (secret.getType().equals(SECRET_TYPE_OPAQUE))
            password = new String(Base64.getDecoder().decode(password.getBytes()));
        return password;
    }


    @Override
    public void onAdd(ConfigMap configMap) {
    }

    @Override
    public void onUpdate(ConfigMap oldConfigMap, ConfigMap newConfigMap) {
    }

    @Override
    public void onDelete(ConfigMap configMap, boolean deletedFinalStateUnknown) {
    }



    @Override
    public void onClose(WatcherException cause) {

    }
}

