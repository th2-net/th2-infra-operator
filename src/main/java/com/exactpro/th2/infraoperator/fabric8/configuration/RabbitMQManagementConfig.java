package com.exactpro.th2.infraoperator.fabric8.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Base64;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

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

    @JsonIgnore
    public String getEncoded() {
        return Base64.getEncoder().encodeToString(String.format("%s:%s", username, password).getBytes(UTF_8));
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
        if (this == o) return true;
        if (!(o instanceof RabbitMQManagementConfig)) return false;
        RabbitMQManagementConfig that = (RabbitMQManagementConfig) o;
        return getPort() == that.getPort() &&
            isPersistence() == that.isPersistence() &&
            Objects.equals(getUsername(), that.getUsername()) &&
            Objects.equals(getPassword(), that.getPassword()) &&
            Objects.equals(getHost(), that.getHost()) &&
            Objects.equals(getRabbitMQNamespacePermissions(), that.getRabbitMQNamespacePermissions());
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class RabbitMQManagementConfigBuilder {

        private String username;
        private String password;
        private int port;
        private String host;
        private boolean persistence;
        @JsonProperty("schemaPermissions")
        private RabbitMQNamespacePermissions rabbitMQNamespacePermissions;

        RabbitMQManagementConfigBuilder() {
        }

        public RabbitMQManagementConfigBuilder username(String username) {
            this.username = username;
            return this;
        }

        public RabbitMQManagementConfigBuilder password(String password) {
            this.password = password;
            return this;
        }

        public RabbitMQManagementConfigBuilder port(int port) {
            this.port = port;
            return this;
        }

        public RabbitMQManagementConfigBuilder host(String host) {
            this.host = host;
            return this;
        }

        public RabbitMQManagementConfigBuilder persistence(boolean persistence) {
            this.persistence = persistence;
            return this;
        }

        public RabbitMQManagementConfigBuilder rabbitMQNamespacePermissions(
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
