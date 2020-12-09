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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookupFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.exactpro.th2.infraoperator.fabric8.util.JsonUtils.writeValueAsDeepMap;

public enum OperatorConfig {
    INSTANCE;

    public static final String QUEUE_PREFIX = "queue_";
    public static final String ROUTING_KEY_PREFIX = "routing-key_";
    public static final String RABBITMQ_SECRET_PASSWORD_KEY = "rabbitmq-password";
    public static final String RABBITMQ_SECRET_USERNAME_KEY = "rabbitmq-username";
    public static final String CONFIG_FILE_SYSTEM_PROPERTY = "infra.operator.config";

    private static final String CONFIG_FILE = "/var/th2/config/infra-operator.yml";
    static final String DEFAULT_RABBITMQ_CONFIGMAP_NAME = "rabbit-mq-app-config";

    private Configuration configuration;

    public String getRabbitMQConfigMapName() {
        return getFullConfig().getRabbitMQConfigMapName();
    }

    public List<String> getNamespacePrefixes() {
        return getFullConfig().getNamespacePrefixes();
    }

    public String getK8sUrl() {
        return getFullConfig().getK8sUrl();
    }

    public String getRabbitMQSecretName() {
        return getSchemaSecrets().getRabbitMQ();
    }

    public String getCassandraSecretName() {
        return getSchemaSecrets().getCassandra();
    }

    public SchemaSecrets getSchemaSecrets() {
        return getFullConfig().getSchemaSecrets();
    }

    public ChartConfig getChartConfig() {
        return getFullConfig().getChartConfig();
    }

    public RabbitMQManagementConfig getRabbitMQManagementConfig() {
        return getFullConfig().getRabbitMQManagementConfig();
    }

    private synchronized Configuration getFullConfig() {
        if (configuration == null)
            configuration = getConfig();
        return configuration;
    }

    Configuration getConfig() {
        try (var in = new FileInputStream(System.getProperty(CONFIG_FILE_SYSTEM_PROPERTY, CONFIG_FILE))) {
            StringSubstitutor stringSubstitutor =
                new StringSubstitutor(StringLookupFactory.INSTANCE.environmentVariableStringLookup());
            String content = stringSubstitutor.replace(new String(in.readAllBytes()));
            return new ObjectMapper(new YAMLFactory()).readValue(content, Configuration.class);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Exception reading configuration " + Configuration.class.getSimpleName(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Configuration {

        @JsonProperty("chart")
        private ChartConfig chartConfig;
        @JsonProperty("rabbitMQManagement")
        private RabbitMQManagementConfig rabbitMQManagementConfig;
        private SchemaSecrets schemaSecrets;
        private List<String> namespacePrefixes;
        private String rabbitMQConfigMapName;
        private String k8sUrl;

        public Configuration() {
            chartConfig = new ChartConfig();
            rabbitMQManagementConfig = new RabbitMQManagementConfig();
            schemaSecrets = new SchemaSecrets();
            namespacePrefixes = new ArrayList<>();
            rabbitMQConfigMapName = DEFAULT_RABBITMQ_CONFIGMAP_NAME;
            k8sUrl = "";
        }

        public ChartConfig getChartConfig() {
            return chartConfig;
        }

        public void setChartConfig(ChartConfig chartConfig) {
            if (chartConfig != null)
                this.chartConfig = chartConfig;
        }

        public RabbitMQManagementConfig getRabbitMQManagementConfig() {
            return rabbitMQManagementConfig;
        }

        public void setRabbitMQManagementConfig(RabbitMQManagementConfig rabbitMQManagementConfig) {
            if (rabbitMQManagementConfig != null)
                this.rabbitMQManagementConfig = rabbitMQManagementConfig;
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

        public String getK8sUrl() {
            return k8sUrl;
        }

        public void setK8sUrl(String k8sUrl) {
            this.k8sUrl = k8sUrl;
        }

        public String getRabbitMQConfigMapName() {
            return rabbitMQConfigMapName;
        }

        public void setRabbitMQConfigMapName(String rabbitMQConfigMapName) {
            if (rabbitMQConfigMapName != null)
                this.rabbitMQConfigMapName = rabbitMQConfigMapName;
        }

        @JsonProperty("configMaps")
        public void setRabbitMQConfigMapName(Map<String, String> configMaps) {
            if (configMaps != null && configMaps.get("rabbitMQ") != null)
                this.rabbitMQConfigMapName = configMaps.get("rabbitMQ");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Configuration)) return false;
            Configuration that = (Configuration) o;
            return Objects.equals(getChartConfig(), that.getChartConfig()) &&
                Objects.equals(getRabbitMQManagementConfig(), that.getRabbitMQManagementConfig()) &&
                Objects.equals(getSchemaSecrets(), that.getSchemaSecrets()) &&
                Objects.equals(getNamespacePrefixes(), that.getNamespacePrefixes()) &&
                Objects.equals(getRabbitMQConfigMapName(), that.getRabbitMQConfigMapName()) &&
                Objects.equals(getK8sUrl(), that.getK8sUrl());
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
}
