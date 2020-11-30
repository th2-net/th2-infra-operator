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
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookupFactory;
import org.jetbrains.annotations.Nullable;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.exactpro.th2.infraoperator.fabric8.util.JsonUtils.JSON_READER;
import static com.exactpro.th2.infraoperator.fabric8.util.JsonUtils.writeValueAsDeepMap;
import static java.nio.charset.StandardCharsets.UTF_8;

public enum OperatorConfig {
    INSTANCE;

    public static final String QUEUE_PREFIX = "queue_";
    public static final String ROUTING_KEY_PREFIX = "routing-key_";
    public static final String MQ_CONFIG_MAP_NAME = "rabbit-mq-app-config";
    public static final String DEFAULT_RABBITMQ_SECRET = "rabbitmq";
    public static final String DEFAULT_CASSANDRA_SECRET = "cassandra";
    public static final String RABBITMQ_SECRET_PASSWORD_KEY = "rabbitmq-password";
    public static final String RABBITMQ_SECRET_USERNAME_KEY = "rabbitmq-username";

    private static final String ROOT_PATH = "/var/th2/config/";

    private ChartConfig chartConfig;
    private MqGlobalConfig mqGlobalConfig;

    private String rabbitMQSecretName = DEFAULT_RABBITMQ_SECRET;
    private String cassandraSecretName = DEFAULT_CASSANDRA_SECRET;

    private Map<String, MqWorkSpaceConfig> mqWorkSpaceConfigPerNamespace = new HashMap<>();

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

    @Nullable
    public synchronized MqWorkSpaceConfig getMqWorkSpaceConfig(String namespace) {
        return mqWorkSpaceConfigPerNamespace.get(namespace);
    }

    public synchronized void setMqWorkSpaceConfig(String namespace, MqWorkSpaceConfig config) {
        mqWorkSpaceConfigPerNamespace.put(namespace, config);
    }

    public synchronized ChartConfig getChartConfig() {
        if (chartConfig == null)
            chartConfig = getConfig(ChartConfig.class, ChartConfig.CONFIG_PATH);
        return chartConfig;
    }

    public synchronized MqGlobalConfig getMqAuthConfig() {
        if (mqGlobalConfig == null)
            mqGlobalConfig = getConfig(MqGlobalConfig.class, MqGlobalConfig.CONFIG_PATH);
        return mqGlobalConfig;
    }

    private <T> T getConfig(Class<T> configType, String path) {
        try (var in = new FileInputStream(path)) {
            StringSubstitutor stringSubstitutor =
                new StringSubstitutor(StringLookupFactory.INSTANCE.environmentVariableStringLookup());
            String content = stringSubstitutor.replace(new String(in.readAllBytes()));
            return JSON_READER.readValue(content, configType);
        } catch (IOException e) {
            throw new IllegalStateException("Exception reading configuration " + configType.getSimpleName(), e);
        }
    }

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

        public String getGit() {
            return git;
        }

        public String getRef() {
            return ref;
        }

        public String getPath() {
            return path;
        }

        public static ChartConfig newInstance(ChartConfig config) {
            return ChartConfig.builder()
                .git(config.getGit())
                .ref(config.getRef())
                .path(config.getPath())
                .build();
        }

        public static ChartConfigBuilder builder() {
            return new ChartConfigBuilder();
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

        public Map<String, Object> toMap() {
            try {
                return writeValueAsDeepMap(this);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Exception converting object", e);
            }
        }

        @Override
        public String toString() {
            return "ChartConfig{" + "git='" + git + '\'' + ", ref='" + ref + '\'' + ", path='" + path + '\'' + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChartConfig)) return false;
            ChartConfig that = (ChartConfig) o;
            return Objects.equals(getGit(), that.getGit()) &&
                Objects.equals(getRef(), that.getRef()) &&
                Objects.equals(getPath(), that.getPath());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getGit(), getRef(), getPath());
        }

        private static class ChartConfigBuilder {

            private String git;
            private String ref;
            private String path;

            ChartConfigBuilder() {
            }

            public ChartConfigBuilder git(String git) {
                this.git = git;
                return this;
            }

            public ChartConfigBuilder ref(String ref) {
                this.ref = ref;
                return this;
            }

            public ChartConfigBuilder path(String path) {
                this.path = path;
                return this;
            }

            public ChartConfig build() {
                return new ChartConfig(git, ref, path);
            }
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

        public MqWorkSpaceConfig(int port, String host, String vHost, String exchangeName, String username,
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

        public void setPort(int port) {
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getVHost() {
            return vHost;
        }

        public void setVHost(String vHost) {
            this.vHost = vHost;
        }

        public String getExchangeName() {
            return exchangeName;
        }

        public void setExchangeName(String exchangeName) {
            this.exchangeName = exchangeName;
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

        public static MqWorkSpaceConfigBuilder builder() {
            return new MqWorkSpaceConfigBuilder();
        }

        @Override
        public String toString() {
            return "MqWorkSpaceConfig{" + "port=" + port + ", host='" + host + '\'' +
                ", vHost='" + vHost + '\'' + ", exchangeName='" + exchangeName + '\'' +
                ", username='" + username + '\'' + ", password='" + password + '\'' + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MqWorkSpaceConfig)) return false;
            MqWorkSpaceConfig that = (MqWorkSpaceConfig) o;
            return getPort() == that.getPort() &&
                Objects.equals(getHost(), that.getHost()) &&
                Objects.equals(vHost, that.vHost) &&
                Objects.equals(getExchangeName(), that.getExchangeName()) &&
                Objects.equals(getUsername(), that.getUsername()) &&
                Objects.equals(getPassword(), that.getPassword());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getPort(), getHost(), vHost, getExchangeName(), getUsername(), getPassword());
        }

        private static class MqWorkSpaceConfigBuilder {

            private int port;
            private String host;
            private String vHost;
            private String exchangeName;
            private String username;
            private String password;

            MqWorkSpaceConfigBuilder() {
            }

            public MqWorkSpaceConfigBuilder port(int port) {
                this.port = port;
                return this;
            }

            public MqWorkSpaceConfigBuilder host(String host) {
                this.host = host;
                return this;
            }

            public MqWorkSpaceConfigBuilder vHost(String vHost) {
                this.vHost = vHost;
                return this;
            }

            public MqWorkSpaceConfigBuilder exchangeName(String exchangeName) {
                this.exchangeName = exchangeName;
                return this;
            }

            public MqWorkSpaceConfigBuilder username(String username) {
                this.username = username;
                return this;
            }

            public MqWorkSpaceConfigBuilder password(String password) {
                this.password = password;
                return this;
            }

            public MqWorkSpaceConfig build() {
                return new MqWorkSpaceConfig(port, host, vHost, exchangeName, username, password);
            }
        }
    }
}
