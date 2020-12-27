package com.exactpro.th2.infraoperator.spec.shared;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = PrometheusConfiguration.Builder.class)
public final class PrometheusConfiguration {
    private String host;
    private String port;
    private String enabled;

    private PrometheusConfiguration(String host, String port, String enabled) {
        this.host = host;
        this.port = port;
        this.enabled = enabled;
    }

    public static Builder builder() {
        return new Builder();
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

    public static class Builder {
        private String host;
        private String port;
        private String enabled;

        @JsonProperty("host")
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        @JsonProperty("port")
        public Builder port(String port) {
            this.port = port;
            return this;
        }

        @JsonProperty("enabled")
        public Builder enabled(String enabled) {
            this.enabled = enabled;
            return this;
        }

        public PrometheusConfiguration build() {
            return new PrometheusConfiguration(host, port, enabled);
        }
    }
}
