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

public enum OperatorConfig {
    INSTANCE;

    public static final String QUEUE_PREFIX = "link";
    public static final String ROUTING_KEY_PREFIX = "key";
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
}
