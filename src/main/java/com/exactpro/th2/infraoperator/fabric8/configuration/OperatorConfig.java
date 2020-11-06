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
import lombok.*;
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
import static io.sundr.codegen.utils.StringUtils.isNullOrEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;


public enum OperatorConfig {

    INSTANCE;


    public static final String ROOT_PATH = "/var/th2/config/";

    public static final String QUEUE_PREFIX = "queue_";

    public static final String ROUTING_KEY_PREFIX = "routing-key_";

    public static final String MQ_CONFIG_MAP_NAME = "rabbit-mq-app-config";


    private ChartConfig chartConfig;

    private MqGlobalConfig mqGlobalConfig;

    private Map<String, MqWorkSpaceConfig> mqWorkSpaceConfigPerNamespace = new HashMap<>();


    public synchronized MqGlobalConfig getMqAuthConfig() {
        if (Objects.isNull(mqGlobalConfig)) {
            mqGlobalConfig = getConfig(MqGlobalConfig.class, MqGlobalConfig.CONFIG_PATH);
        }
        return mqGlobalConfig;
    }

    public synchronized ChartConfig getChartConfig() {
        if (Objects.isNull(chartConfig)) {
            chartConfig = getConfig(ChartConfig.class, ChartConfig.CONFIG_PATH);
        }
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
            var stringSubstitutor = new StringSubstitutor(StringLookupFactory.INSTANCE.environmentVariableStringLookup());
            var content = stringSubstitutor.replace(new String(in.readAllBytes()));
            return JSON_READER.readValue(content, configType);
        } catch (IOException e) {
            throw new IllegalStateException("Can not read " + configType.getSimpleName() + " configuration", e);
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

            if (!isNullOrEmpty(chartConfig.getGit())) {
                config.git = chartConfig.getGit();
            }
            if (!isNullOrEmpty(chartConfig.getRef())) {
                config.ref = chartConfig.getRef();
            }
            if (!isNullOrEmpty(chartConfig.getPath())) {
                config.path = chartConfig.getPath();
            }

            return config;
        }

        @SneakyThrows
        public Map<String, Object> toMap() {
            return writeValueAsDeepMap(this);
        }

    }

    @Getter
    @Builder
    @ToString
    @EqualsAndHashCode
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MqGlobalConfig {
        public static final String CONFIG_PATH = ROOT_PATH + "rabbitMQ-mng.json";


        private String username;
        private String password;
        private int port;
        private boolean persistence;


        protected MqGlobalConfig() {
        }

        protected MqGlobalConfig(String username, String password, int port, boolean persistence) {
            this.username = username;
            this.password = password;
            this.port = port;
            this.persistence = persistence;
        }

        public String getEncoded() {
            return Base64.getEncoder().encodeToString(String.format("%s:%s", username, password).getBytes(UTF_8));
        }
    }

    @Getter
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


        protected MqWorkSpaceConfig() {
        }

        protected MqWorkSpaceConfig(int port, String host, String vHost, String exchangeName) {
            this.port = port;
            this.host = host;
            this.vHost = vHost;
            this.exchangeName = exchangeName;
        }
    }

}
