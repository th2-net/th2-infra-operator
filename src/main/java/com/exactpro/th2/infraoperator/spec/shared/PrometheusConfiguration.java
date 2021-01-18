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
