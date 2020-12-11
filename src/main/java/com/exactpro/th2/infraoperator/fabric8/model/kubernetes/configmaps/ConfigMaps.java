package com.exactpro.th2.infraoperator.fabric8.model.kubernetes.configmaps;

import com.exactpro.th2.infraoperator.fabric8.configuration.RabbitMQConfig;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public enum ConfigMaps {
    INSTANCE;

    private Map<String, RabbitMQConfig> rabbitMQConfigs = new HashMap<>();

    @Nullable
    public synchronized RabbitMQConfig getRabbitMQConfig4Namespace(String namespace) {
        return rabbitMQConfigs.get(namespace);
    }

    public synchronized void setRabbitMQConfig4Namespace(String namespace, RabbitMQConfig config) {
        rabbitMQConfigs.put(namespace, config);
    }
}