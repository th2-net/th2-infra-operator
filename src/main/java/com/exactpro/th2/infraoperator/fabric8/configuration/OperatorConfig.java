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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookupFactory;
import org.jetbrains.annotations.Nullable;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

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

    private Configuration configuration;

    private Map<String, MqWorkSpaceConfig> mqWorkSpaceConfigPerNamespace = new HashMap<>();

    public List<String> getNamespacePrefixes() {
        return getFullConfig(null).getNamespacePrefixes();
    }

    public String getRabbitMQSecretName() {
        return getSchemaSecrets().getRabbitMQ();
    }

    public String getCassandraSecretName() {
        return getSchemaSecrets().getCassandra();
    }

    public SchemaSecrets getSchemaSecrets() {
        return getFullConfig(null).getSchemaSecrets();
    }

    @Nullable
    public synchronized MqWorkSpaceConfig getMqWorkSpaceConfig(String namespace) {
        return mqWorkSpaceConfigPerNamespace.get(namespace);
    }

    public synchronized void setMqWorkSpaceConfig(String namespace, MqWorkSpaceConfig config) {
        mqWorkSpaceConfigPerNamespace.put(namespace, config);
    }

    public ChartConfig getChartConfig() {
        return getFullConfig(null).getChartConfig();
    }

    public MqGlobalConfig getMqAuthConfig() {
        return getFullConfig(null).getMqGlobalConfig();
    }

    public synchronized Configuration getFullConfig(String path) {
        if (configuration == null)
            configuration = getConfig(path);
        return configuration;
    }

    private Configuration getConfig(String path) {
        try (var in = new FileInputStream(getConfigPath(path))) {
            StringSubstitutor stringSubstitutor =
                new StringSubstitutor(StringLookupFactory.INSTANCE.environmentVariableStringLookup());
            String content = stringSubstitutor.replace(new String(in.readAllBytes()));
            return new ObjectMapper(new YAMLFactory()).readValue(content, Configuration.class);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Exception reading configuration " + Configuration.class.getSimpleName(), e);
        }
    }

    private String getConfigPath(String path) {
        return path != null ? path : Configuration.CONFIG_PATH;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Configuration {

        private static final String CONFIG_PATH = "/var/th2/config/?.yml";

        @JsonProperty("chart")
        private ChartConfig chartConfig;
        @JsonProperty("rabbitMQManagement")
        private MqGlobalConfig mqGlobalConfig;
        private SchemaSecrets schemaSecrets;
        private List<String> namespacePrefixes;

        public Configuration() {
            chartConfig = new ChartConfig();
            mqGlobalConfig = new MqGlobalConfig();
            schemaSecrets = new SchemaSecrets();
            namespacePrefixes = new ArrayList<>();
        }

        public ChartConfig getChartConfig() {
            return chartConfig;
        }

        public void setChartConfig(ChartConfig chartConfig) {
            if (chartConfig != null)
                this.chartConfig = chartConfig;
        }

        public MqGlobalConfig getMqGlobalConfig() {
            return mqGlobalConfig;
        }

        public void setMqGlobalConfig(MqGlobalConfig mqGlobalConfig) {
            if (mqGlobalConfig != null)
                this.mqGlobalConfig = mqGlobalConfig;
        }

        public SchemaSecrets getSchemaSecrets() {
            return schemaSecrets;
        }

        public void setSchemaSecrets(SchemaSecrets schemaSecrets) {
            if (schemaSecrets != null)
                this.schemaSecrets = schemaSecrets;
        }

        public List<String> getNamespacePrefixes() {
            return namespacePrefixes;
        }

        public void setNamespacePrefixes(List<String> namespacePrefixes) {
            if (namespacePrefixes != null)
                this.namespacePrefixes = namespacePrefixes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Configuration)) return false;
            Configuration that = (Configuration) o;
            return Objects.equals(getChartConfig(), that.getChartConfig()) &&
                Objects.equals(getMqGlobalConfig(), that.getMqGlobalConfig()) &&
                Objects.equals(getSchemaSecrets(), that.getSchemaSecrets()) &&
                Objects.equals(getNamespacePrefixes(), that.getNamespacePrefixes());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChartConfig {

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

        public static class ChartConfigBuilder {

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

        private String username;
        private String password;
        private int port;
        private String host;
        private boolean persistence;
        @JsonProperty("schemaPermissions")
        private MqSchemaUserPermissions schemaUserPermissions;

        protected MqGlobalConfig() {
            schemaUserPermissions = new MqSchemaUserPermissions();
        }

        protected MqGlobalConfig(String username, String password, int port, String host, boolean persistence,
                                 MqSchemaUserPermissions schemaUserPermissions) {
            this.username = username;
            this.password = password;
            this.port = port;
            this.host = host;
            this.persistence = persistence;
            this.schemaUserPermissions =
                schemaUserPermissions != null ? schemaUserPermissions : new MqSchemaUserPermissions();
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

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
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

        public static MqGlobalConfigBuilder builder() {
            return new MqGlobalConfigBuilder();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MqGlobalConfig)) return false;
            MqGlobalConfig that = (MqGlobalConfig) o;
            return getPort() == that.getPort() &&
                isPersistence() == that.isPersistence() &&
                Objects.equals(getUsername(), that.getUsername()) &&
                Objects.equals(getPassword(), that.getPassword()) &&
                Objects.equals(getHost(), that.getHost()) &&
                Objects.equals(getSchemaUserPermissions(), that.getSchemaUserPermissions());
        }

        public static class MqGlobalConfigBuilder {

            private String username;
            private String password;
            private int port;
            private String host;
            private boolean persistence;
            private MqSchemaUserPermissions schemaUserPermissions;

            MqGlobalConfigBuilder() {
            }

            public MqGlobalConfigBuilder username(String username) {
                this.username = username;
                return this;
            }

            public MqGlobalConfigBuilder password(String password) {
                this.password = password;
                return this;
            }

            public MqGlobalConfigBuilder port(int port) {
                this.port = port;
                return this;
            }

            public MqGlobalConfigBuilder host(String host) {
                this.host = host;
                return this;
            }

            public MqGlobalConfigBuilder persistence(boolean persistence) {
                this.persistence = persistence;
                return this;
            }

            public MqGlobalConfigBuilder schemaUserPermissions(MqSchemaUserPermissions schemaUserPermissions) {
                this.schemaUserPermissions = schemaUserPermissions;
                return this;
            }

            public MqGlobalConfig build() {
                return new MqGlobalConfig(username, password, port, host, persistence, schemaUserPermissions);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MqSchemaUserPermissions {

        private String configure = "";
        private String read = ".*";
        private String write = ".*";

        public MqSchemaUserPermissions() {
        }

        public MqSchemaUserPermissions(String configure, String read, String write) {
            this.configure = configure == null ? "" : configure;
            this.read = read == null ? "" : read;
            this.write = write == null ? "" : write;
        }

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

        public static MqSchemaUserPermissionsBuilder builder() {
            return new MqSchemaUserPermissionsBuilder();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MqSchemaUserPermissions)) return false;
            MqSchemaUserPermissions that = (MqSchemaUserPermissions) o;
            return Objects.equals(getConfigure(), that.getConfigure()) &&
                Objects.equals(getRead(), that.getRead()) &&
                Objects.equals(getWrite(), that.getWrite());
        }

        public static class MqSchemaUserPermissionsBuilder {

            private String configure;
            private String read;
            private String write;

            MqSchemaUserPermissionsBuilder() {
            }

            public MqSchemaUserPermissionsBuilder configure(String configure) {
                this.configure = configure;
                return this;
            }

            public MqSchemaUserPermissionsBuilder read(String read) {
                this.read = read;
                return this;
            }

            public MqSchemaUserPermissionsBuilder write(String write) {
                this.write = write;
                return this;
            }

            public MqSchemaUserPermissions build() {
                return new MqSchemaUserPermissions(configure, read, write);
            }
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

        public static class MqWorkSpaceConfigBuilder {

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
