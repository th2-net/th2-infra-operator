package com.exactpro.th2.infraoperator.fabric8.model.kubernetes.configmaps;

import java.util.Map;

public enum ConfigMaps {
    INSTANCE;

    public static final String PROMETHEUS_CONFIGMAP_NAME = "prometheus-app-config";
    public static final String PROMETHEUS_JSON_KEY = "prometheus.json";
    private static Map<String, Object> prometheus;

    public static synchronized Map<String, Object> getPrometheus() {
        return prometheus;
    }

    public static synchronized void setPrometheus(Map<String, Object> prometheus) {
        ConfigMaps.prometheus = prometheus;
    }

}