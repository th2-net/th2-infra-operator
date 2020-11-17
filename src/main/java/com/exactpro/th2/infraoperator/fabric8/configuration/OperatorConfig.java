/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.infraoperator.fabric8.configuration;

import com.exactpro.th2.infraoperator.fabric8.util.Strings;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookupFactory;
import org.jetbrains.annotations.Nullable;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static com.exactpro.th2.infraoperator.fabric8.util.JsonUtils.JSON_READER;
import static com.exactpro.th2.infraoperator.fabric8.util.JsonUtils.writeValueAsDeepMap;
import static java.nio.charset.StandardCharsets.UTF_8;


public enum OperatorConfig {
    INSTANCE;

    public static final String QUEUE_PREFIX = "queue_";
    public static final String ROUTING_KEY_PREFIX = "routing-key_";
    public static final String MQ_CONFIG_MAP_NAME = "rabbit-mq-app-config";
    private static final String ROOT_PATH = "/var/th2/config/";
    public static final String DEFAULT_RABBITMQ_SECRET = "rabbitmq";
    public static final String DEFAULT_CASSANDRA_SECRET = "cassandra";

    public static final String RABBITMQ_SECRET_PASSWORD_KEY = "rabbitmq-password";
    public static final String RABBITMQ_SECRET_USERNAME_KEY = "rabbitmq-username";

    private ChartConfig chartConfig;
    private MqGlobalConfig mqGlobalConfig;

    private String rabbitMQSecretName = DEFAULT_RABBITMQ_SECRET;
    private String cassandraSecretName = DEFAULT_CASSANDRA_SECRET;

    public String getRabbitMQSecretName() {
        return rabbitMQSecretName;
    }

    public void setRabbitMQSecretName(String rabbitMQSecretName) {
        this.rabbitMQSecretName = rabbitMQSecretName;
    }

    public String getCassandraSecretName() {
        return cassandraSecretName;
    }

    public void setCassandraSecretName(String cassandraSecretName) {
        this.cassandraSecretName = cassandraSecretName;
    }
    private Map<String, MqWorkSpaceConfig> mqWorkSpaceConfigPerNamespace = new HashMap<>();


    public synchronized MqGlobalConfig getMqAuthConfig() {
        if (mqGlobalConfig == null)
            mqGlobalConfig = getConfig(MqGlobalConfig.class, MqGlobalConfig.CONFIG_PATH);
        return mqGlobalConfig;
    }

    public synchronized ChartConfig getChartConfig() {
        if (chartConfig == null)
            chartConfig = getConfig(ChartConfig.class, ChartConfig.CONFIG_PATH);
        return chartConfig;
    }


    @Nullable
    public synchronized MqWorkSpaceConfig getMqWorkSpaceConfig(String namespace) {
        return mqWorkSpaceConfigPerNamespace.get(namespace);
    }


    public synchronized void setMqWorkSpaceConfig(String namespace, MqWorkSpaceConfig config) {
        mqWorkSpaceConfigPerNamespace.put(namespace, config);
    }


    private <T> T getConfig(Class<T> configType, String path) {
        try (var in = new FileInputStream(path)) {
            StringSubstitutor stringSubstitutor = new StringSubstitutor(StringLookupFactory.INSTANCE.environmentVariableStringLookup());
            String content = stringSubstitutor.replace(new String(in.readAllBytes()));
            return JSON_READER.readValue(content, configType);
        } catch (IOException e) {
            throw new IllegalStateException("Exception reading configuration " + configType.getSimpleName(), e);
        }
    }


    @Getter
    @Builder
    @ToString
    @EqualsAndHashCode
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChartConfig {

        public static final String CONFIG_PATH = ROOT_PATH + "infra-operator.json";

        private String git;
        private String ref;
        private String path;


        protected ChartConfig() {
        }

        protected ChartConfig(String git, String ref, String path) {
            this.git = git;
            this.ref = ref;
            this.path = path;
        }


        public static ChartConfig newInstance(ChartConfig config) {
            return ChartConfig.builder()
                    .git(config.getGit())
                    .ref(config.getRef())
                    .path(config.getPath())
                    .build();
        }

        public ChartConfig updateWithAndCreate(ChartConfig chartConfig) {

            var config = ChartConfig.newInstance(this);

            if (!Strings.isNullOrEmpty(chartConfig.getGit()))
                config.git = chartConfig.getGit();

            if (!Strings.isNullOrEmpty(chartConfig.getRef()))
                config.ref = chartConfig.getRef();

            if (!Strings.isNullOrEmpty(chartConfig.getPath()))
                config.path = chartConfig.getPath();

            return config;
        }


        @SneakyThrows
        public Map<String, Object> toMap() {
            return writeValueAsDeepMap(this);
        }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MqGlobalConfig {
        public static final String CONFIG_PATH = ROOT_PATH + "rabbitMQ-mng.json";

        private String username;
        private String password;
        private int port;
        private boolean persistence;
        private MqSchemaUserPermissions schemaUserPermissions;


        protected MqGlobalConfig() {
            schemaUserPermissions = new MqSchemaUserPermissions();
        }

        @JsonIgnore
        public String getEncoded() {
            return Base64.getEncoder().encodeToString(String.format("%s:%s", username, password).getBytes(UTF_8));
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isPersistence() {
            return persistence;
        }

        public void setPersistence(boolean persistence) {
            this.persistence = persistence;
        }

        public MqSchemaUserPermissions getSchemaUserPermissions() {
            return schemaUserPermissions;
        }

        public void setSchemaUserPermissions(MqSchemaUserPermissions schemaUserPermissions) {
            if (schemaUserPermissions != null)
                this.schemaUserPermissions = schemaUserPermissions;
        }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MqSchemaUserPermissions {
        private String configure = "";
        private String read = ".*";
        private String write = ".*";

        public String getConfigure() {
            return configure;
        }

        public void setConfigure(String configure) {
            this.configure = (configure == null) ? "" : configure;
        }

        public String getRead() {
            return read;
        }

        public void setRead(String read) {
            this.read = (read == null) ? "" : read;
        }

        public String getWrite() {
            return write;
        }

        public void setWrite(String write) {
            this.write = (write == null) ? "" : write;
        }
    }


    @Getter
    @Setter
    @Builder
    @ToString
    @EqualsAndHashCode
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MqWorkSpaceConfig {

        public static final String CONFIG_MAP_RABBITMQ_PROP_NAME = "rabbitMQ.json";

        private int port;
        private String host;
        @JsonProperty("vHost")
        private String vHost;
        @JsonProperty("exchangeName")
        private String exchangeName;

        private String username;
        private String password;

        protected MqWorkSpaceConfig() {
        }

        public MqWorkSpaceConfig(int port, String host, String vHost, String exchangeName, String username, String password) {
            this.port = port;
            this.host = host;
            this.vHost = vHost;
            this.exchangeName = exchangeName;
            this.username = username;
            this.password = password;
        }
    }

}
