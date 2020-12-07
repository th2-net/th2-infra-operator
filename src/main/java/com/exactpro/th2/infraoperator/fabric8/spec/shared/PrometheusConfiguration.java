package com.exactpro.th2.infraoperator.fabric8.spec.shared;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrometheusConfiguration {
    private String host;
    private String port;
    private String enabled;

    public PrometheusConfiguration() { }

    public PrometheusConfiguration(String host, String port, String enabled) {
        this.host = host;
        this.port = port;
        this.enabled = enabled;
    }

    public static PrometheusConfigurationBuilder builder() {
        return new PrometheusConfigurationBuilder();
    }

    public String getHost() {
        return this.host;
    }

    public String getPort() {
        return this.port;
    }

    public String getEnabled() {
        return this.enabled;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public void setEnabled(String enabled) {
        this.enabled = enabled;
    }

    public static class PrometheusConfigurationBuilder {
        private String host;
        private String port;
        private String enabled;

        PrometheusConfigurationBuilder() {
        }

        public PrometheusConfigurationBuilder host(String host) {
            this.host = host;
            return this;
        }

        public PrometheusConfigurationBuilder port(String port) {
            this.port = port;
            return this;
        }

        public PrometheusConfigurationBuilder enabled(String enabled) {
            this.enabled = enabled;
            return this;
        }

        public PrometheusConfiguration build() {
            return new PrometheusConfiguration(host, port, enabled);
        }
    }
}
