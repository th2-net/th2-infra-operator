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

@JsonDeserialize(builder = RabbitMQManagementConfig.RabbitMQManagementConfigBuilder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RabbitMQManagementConfig {

    private String username;

    private String password;

    private int port;

    private String host;

    private boolean persistence;

    private RabbitMQNamespacePermissions rabbitMQNamespacePermissions;

    protected RabbitMQManagementConfig() {
        rabbitMQNamespacePermissions = new RabbitMQNamespacePermissions();
    }

    protected RabbitMQManagementConfig(String username, String password, int port, String host, boolean persistence,
                                       RabbitMQNamespacePermissions rabbitMQNamespacePermissions) {
        this.username = username;
        this.password = password;
        this.port = port;
        this.host = host;
        this.persistence = persistence;
        this.rabbitMQNamespacePermissions = rabbitMQNamespacePermissions != null ?
                rabbitMQNamespacePermissions : new RabbitMQNamespacePermissions();
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public boolean isPersistence() {
        return persistence;
    }

    public RabbitMQNamespacePermissions getRabbitMQNamespacePermissions() {
        return rabbitMQNamespacePermissions;
    }

    public static RabbitMQManagementConfigBuilder builder() {
        return new RabbitMQManagementConfigBuilder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RabbitMQManagementConfig)) {
            return false;
        }
        RabbitMQManagementConfig that = (RabbitMQManagementConfig) o;
        return getPort() == that.getPort()
                && isPersistence() == that.isPersistence()
                && Objects.equals(getUsername(), that.getUsername())
                && Objects.equals(getPassword(), that.getPassword())
                && Objects.equals(getHost(), that.getHost())
                && Objects.equals(getRabbitMQNamespacePermissions(), that.getRabbitMQNamespacePermissions());
    }

    public static class RabbitMQManagementConfigBuilder {

        private String username;

        private String password;

        private int port;

        private String host;

        private boolean persistence;

        @JsonProperty("schemaPermissions")
        private RabbitMQNamespacePermissions rabbitMQNamespacePermissions;

        RabbitMQManagementConfigBuilder() { }

        public RabbitMQManagementConfigBuilder withUsername(String username) {
            this.username = username;
            return this;
        }

        public RabbitMQManagementConfigBuilder withPassword(String password) {
            this.password = password;
            return this;
        }

        public RabbitMQManagementConfigBuilder withPort(int port) {
            this.port = port;
            return this;
        }

        public RabbitMQManagementConfigBuilder withHost(String host) {
            this.host = host;
            return this;
        }

        public RabbitMQManagementConfigBuilder withPersistence(boolean persistence) {
            this.persistence = persistence;
            return this;
        }

        public RabbitMQManagementConfigBuilder withRabbitMQNamespacePermissions(
                RabbitMQNamespacePermissions rabbitMQNamespacePermissions) {
            this.rabbitMQNamespacePermissions = rabbitMQNamespacePermissions;
            return this;
        }

        public RabbitMQManagementConfig build() {
            return new RabbitMQManagementConfig(username, password, port, host, persistence,
                    rabbitMQNamespacePermissions);
        }
    }
}
