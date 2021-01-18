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

package com.exactpro.th2.infraoperator.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Objects;

@JsonDeserialize(builder = RabbitMQConfig.RabbitMQConfigBuilder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RabbitMQConfig {

    public static final String CONFIG_MAP_RABBITMQ_PROP_NAME = "rabbitMQ.json";

    private int port;
    private String host;
    @JsonProperty("vHost")
    private String vHost;
    private String exchangeName;
    private String username;
    private String password;

    protected RabbitMQConfig() {
    }

    public RabbitMQConfig(int port, String host, String vHost, String exchangeName, String username,
                          String password) {
        this.port = port;
        this.host = host;
        this.vHost = vHost;
        this.exchangeName = exchangeName;
        this.username = username;
        this.password = password;
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public String getVHost() {
        return vHost;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public static RabbitMQConfigBuilder builder() {
        return new RabbitMQConfigBuilder();
    }

    @Override
    public String toString() {
        return "RabbitMQConfig{" + "port=" + port + ", host='" + host + '\'' +
            ", vHost='" + vHost + '\'' + ", exchangeName='" + exchangeName + '\'' +
            ", username='" + username + '\'' + ", password='" + password + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RabbitMQConfig)) return false;
        RabbitMQConfig that = (RabbitMQConfig) o;
        return getPort() == that.getPort() &&
            Objects.equals(getHost(), that.getHost()) &&
            Objects.equals(getVHost(), that.getVHost()) &&
            Objects.equals(getExchangeName(), that.getExchangeName()) &&
            Objects.equals(getUsername(), that.getUsername()) &&
            Objects.equals(getPassword(), that.getPassword());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPort(), getHost(), getVHost(), getExchangeName(), getUsername(), getPassword());
    }

    public static class RabbitMQConfigBuilder {

        private int port;
        private String host;
        private String vHost;
        private String exchangeName;
        private String username;
        private String password;

        RabbitMQConfigBuilder() {
        }

        public RabbitMQConfigBuilder withPort(int port) {
            this.port = port;
            return this;
        }

        public RabbitMQConfigBuilder withHost(String host) {
            this.host = host;
            return this;
        }

        @JsonProperty("vHost")
        public RabbitMQConfigBuilder withVHost(String vHost) {
            this.vHost = vHost;
            return this;
        }

        public RabbitMQConfigBuilder withExchangeName(String exchangeName) {
            this.exchangeName = exchangeName;
            return this;
        }

        public RabbitMQConfigBuilder withUsername(String username) {
            this.username = username;
            return this;
        }

        public RabbitMQConfigBuilder withPassword(String password) {
            this.password = password;
            return this;
        }

        public RabbitMQConfig build() {
            return new RabbitMQConfig(port, host, vHost, exchangeName, username, password);
        }
    }
}
