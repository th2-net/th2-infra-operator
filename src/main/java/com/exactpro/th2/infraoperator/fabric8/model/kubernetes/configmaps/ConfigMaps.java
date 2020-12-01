package com.exactpro.th2.infraoperator.fabric8.model.kubernetes.configmaps;

import java.util.HashMap;
import java.util.Map;

public enum ConfigMaps {
    INSTANCE;

    public static final String PROMETHEUS_CONFIGMAP_NAME = "prometheus-app-config";
    public static final String PROMETHEUS_JSON_KEY = "prometheus.json";
    public static final String PROMETHEUS_JSON_ENABLED_PROPERTY = "enabled";
    private static Map<String, Object> prometheus;

    public static synchronized Map<String, Object> getPrometheusParams() {
        if (prometheus == null)
            return null;
        Map<String, Object> copy = new HashMap<>();
        copy.putAll(prometheus);
        return copy;
    }

    public static synchronized void setPrometheusParams(Map<String, Object> params) {
        ConfigMaps.prometheus = params;
    }

}