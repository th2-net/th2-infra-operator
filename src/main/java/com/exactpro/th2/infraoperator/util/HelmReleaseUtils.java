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

package com.exactpro.th2.infraoperator.util;

import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.DictionaryEntity;
import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.MultiDictionaryEntity;
import com.exactpro.th2.infraoperator.model.box.configuration.grpc.GrpcEndpointMapping;
import com.exactpro.th2.infraoperator.model.box.configuration.grpc.GrpcExternalEndpointMapping;
import com.exactpro.th2.infraoperator.model.box.configuration.mq.MessageRouterConfiguration;
import com.exactpro.th2.infraoperator.model.box.configuration.mq.QueueConfiguration;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease;
import com.exactpro.th2.infraoperator.spec.helmrelease.InstantiableMap;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.apache.commons.text.lookup.StringLookupFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.*;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.JsonUtils.JSON_READER;

public class HelmReleaseUtils {

    private static final Logger logger = LoggerFactory.getLogger(HelmReleaseUtils.class);

    private static final String HOST_NETWORK_ALIAS = "hostNetwork";

    private static final String ENDPOINTS_ALIAS = "endpoints";

    private static final String ADDRESS_ALIAS = "address";

    private static final String GRPC_ALIAS = "grpc";

    private static final String SECRET_VALUE_PREFIX = "secret_value";

    private static final String SECRET_PATH_PREFIX = "secret_path";

    private static final String SHARED_MEMORY_ALIAS = "sharedMemory";

    private static final String K8S_PROBES = "k8sProbes";

    private static final String HOST_NETWORK = "hostNetwork";

    private static final ObjectMapper mapper = new ObjectMapper();

    private HelmReleaseUtils() {
    }

    public static boolean isHostNetwork(Map<String, Object> boxExtendedSettings) {
        return boxExtendedSettings != null && getFieldAsBoolean(boxExtendedSettings, HOST_NETWORK_ALIAS);
    }

    public static boolean isExternalBox(Map<String, Object> boxExtendedSettings) {
        return boxExtendedSettings != null && getFieldAsBoolean(boxExtendedSettings, EXTERNAL_BOX_ALIAS, ENABLED_ALIAS);
    }

    public static GrpcEndpointMapping getGrpcMapping(Map<String, Object> boxSettings) {
        if (boxSettings == null) {
            return null;
        }
        JsonNode endpointsNode = getFieldAsNode(boxSettings, SERVICE_ALIAS, ENDPOINTS_ALIAS);
        if (endpointsNode != null) {
            List<GrpcEndpointMapping> grpcEndpointMappings = JSON_READER.convertValue(endpointsNode,
                    new TypeReference<>() {
                    });
            for (GrpcEndpointMapping grpcEndpointMapping : grpcEndpointMappings) {
                if (grpcEndpointMapping.getName().equals(GRPC_ALIAS)) {
                    return grpcEndpointMapping;
                }
            }
        }
        return null;
    }

    public static GrpcExternalEndpointMapping getGrpcExternalMapping(Map<String, Object> boxSettings) {
        if (boxSettings == null) {
            return null;
        }
        JsonNode endpointsNode = getFieldAsNode(boxSettings, EXTERNAL_BOX_ALIAS, ENDPOINTS_ALIAS);
        if (endpointsNode != null) {
            List<GrpcExternalEndpointMapping> externalEndpoints = JSON_READER.convertValue(endpointsNode,
                    new TypeReference<>() {
                    });
            for (GrpcExternalEndpointMapping grpcExternalEndpointMapping : externalEndpoints) {
                if (grpcExternalEndpointMapping.getName().equals(GRPC_ALIAS)) {
                    return grpcExternalEndpointMapping;
                }
            }
        }
        return null;
    }

    public static String getExternalHost(Map<String, Object> boxSettings) {
        if (boxSettings == null) {
            return null;
        }
        JsonNode address = getFieldAsNode(boxSettings, EXTERNAL_BOX_ALIAS, ADDRESS_ALIAS);
        if (address != null) {
            return JSON_READER.convertValue(address, String.class);
        }
        return null;
    }

    public static void hostNetworkEndpointNotFound(Th2CustomResource box) {
        String message = String.format(
                "Could not find HostNetworkEndpoint configuration for [%S], please check '%s' section in CR",
                annotationFor(box), EXTENDED_SETTINGS_ALIAS + "." + SERVICE_ALIAS + "." + ENDPOINTS_ALIAS);
        throw new RuntimeException(message);
    }

    public static void externalBoxEndpointNotFound(Th2CustomResource box) {
        String message = String.format(
                "Could not find ExternalBoxEndpoint configuration for [%S], please check '%s' section in CR",
                annotationFor(box), EXTENDED_SETTINGS_ALIAS + "." + EXTERNAL_BOX_ALIAS + "." + ENDPOINTS_ALIAS);
        throw new RuntimeException(message);
    }

    private static JsonNode getFieldAsNode(Object sourceObj, String... fields) {
        JsonNode currentField = JSON_READER.convertValue(sourceObj, JsonNode.class);
        for (String field : fields) {
            currentField = currentField.get(field);
            if (currentField == null) {
                return null;
            }
        }
        return currentField;
    }

    private static boolean getFieldAsBoolean(Object sourceObj, String... fields) {
        return getFieldAsBoolean(sourceObj, false, fields);
    }

    private static boolean getFieldAsBoolean(Object sourceObj, boolean defaultValue, String... fields) {
        JsonNode currentField = JSON_READER.convertValue(sourceObj, JsonNode.class);
        for (String field : fields) {
            currentField = currentField.get(field);
            if (currentField == null) {
                return defaultValue;
            }
        }
        return JSON_READER.convertValue(currentField, Boolean.class);
    }

    private static Map<String, Object> getSectionReference(Map<String, Object> values, String... fields) {
        Map<String, Object> currentSection = (Map<String, Object>) values.get(fields[0]);
        for (int i = 1; i < fields.length; i++) {
            if (currentSection != null) {
                currentSection = (Map<String, Object>) currentSection.get(fields[i]);
            }
        }
        return currentSection;
    }

    public static <R> void convertField(HelmRelease helmRelease, Function<String, R> converter, String fieldName,
                                        String... fields) {
        Map<String, Object> section = getSectionReference(helmRelease.getValuesSection(), fields);
        if (section != null) {
            var currentValue = section.get(fieldName);
            if (currentValue != null) {
                section.put(fieldName, converter.apply(currentValue.toString()));
            }
        }
    }

    public static void convertBooleanFields(HelmRelease helmRelease) {
        convertField(helmRelease, Boolean::valueOf, ENABLED_ALIAS, ROOT_PROPERTIES_ALIAS,
                EXTENDED_SETTINGS_ALIAS, SERVICE_ALIAS);
        convertField(helmRelease, Boolean::valueOf, ENABLED_ALIAS, ROOT_PROPERTIES_ALIAS,
                EXTENDED_SETTINGS_ALIAS, EXTERNAL_BOX_ALIAS);
        convertField(helmRelease, Boolean::valueOf, ENABLED_ALIAS, ROOT_PROPERTIES_ALIAS,
                EXTENDED_SETTINGS_ALIAS, SHARED_MEMORY_ALIAS);
        convertField(helmRelease, Boolean::valueOf, K8S_PROBES, ROOT_PROPERTIES_ALIAS,
                EXTENDED_SETTINGS_ALIAS);
        convertField(helmRelease, Boolean::valueOf, HOST_NETWORK, ROOT_PROPERTIES_ALIAS,
                EXTENDED_SETTINGS_ALIAS);
    }

    public static Map<String, Object> extractConfigSection(HelmRelease helmRelease, String key) {
        var values = (Map<String, Object>) helmRelease.getValuesSection();
        var componentConfigs = (Map<String, Object>) values.get(ROOT_PROPERTIES_ALIAS);
        return (Map<String, Object>) componentConfigs.get(key);
    }

    public static String extractComponentName(HelmRelease helmRelease) {
        var values = (Map<String, Object>) helmRelease.getValuesSection();
        var componentConfigs = (Map<String, Object>) values.get(ROOT_PROPERTIES_ALIAS);
        return (String) componentConfigs.get("name");
    }

    public static Collection<DictionaryEntity> extractOldDictionariesConfig(HelmRelease helmRelease) {
        var values = (Map<String, Object>) helmRelease.getValuesSection();
        var componentConfigs = (Map<String, Object>) values.get(ROOT_PROPERTIES_ALIAS);
        return (Collection<DictionaryEntity>) componentConfigs.get(DICTIONARIES_ALIAS);
    }

    public static List<MultiDictionaryEntity> extractMultiDictionariesConfig(HelmRelease helmRelease) {
        var values = (Map<String, Object>) helmRelease.getValuesSection();
        var componentConfigs = (Map<String, Object>) values.get(ROOT_PROPERTIES_ALIAS);
        return (List<MultiDictionaryEntity>) componentConfigs.get(MULTI_DICTIONARIES_ALIAS);
    }

    public static boolean needsToBeDeleted(HelmRelease newHelmRelease, HelmRelease oldHelmRelease) {
        if (oldHelmRelease == null) {
            return false;
        }
        return isAlreadyFailed(oldHelmRelease)
                || containsFailingService(newHelmRelease, oldHelmRelease)
                || containsFailingExternalBox(newHelmRelease, oldHelmRelease)
                || containsFailingHostNetwork(newHelmRelease, oldHelmRelease);
    }

    private static boolean isAlreadyFailed(HelmRelease oldHelmRelease) {
        InstantiableMap statusSection = oldHelmRelease.getStatus();
        if (statusSection == null ||
                statusSection.get("phase") == null ||
                statusSection.get("releaseStatus") == null ||
                !statusSection.get("phase").equals("Succeeded") ||
                !statusSection.get("releaseStatus").equals("deployed")
        ) {
            logger.warn("HelmRelease \"{}\" was failed. will be recreated",
                    annotationFor(oldHelmRelease));
            return true;
        }
        return false;
    }

    private static boolean containsFailingService(HelmRelease newHelmRelease, HelmRelease oldHelmRelease) {
        Map<String, Object> newServiceSection = getSectionReference(newHelmRelease.getValuesSection(),
                ROOT_PROPERTIES_ALIAS, EXTENDED_SETTINGS_ALIAS, SERVICE_ALIAS);
        Map<String, Object> oldServiceSection = getSectionReference(oldHelmRelease.getValuesSection(),
                ROOT_PROPERTIES_ALIAS, EXTENDED_SETTINGS_ALIAS, SERVICE_ALIAS);
        if (newServiceSection == null && oldServiceSection == null) {
            return false;
        }
        if (newServiceSection == null ^ oldServiceSection == null) {
            return true;
        }
        try {
            String newServiceSectionStr = mapper.writeValueAsString(newServiceSection);
            String oldServiceSectionStr = mapper.writeValueAsString(oldServiceSection);
            return !newServiceSectionStr.equals(oldServiceSectionStr);
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean containsFailingExternalBox(HelmRelease newHelmRelease, HelmRelease oldHelmRelease) {
        boolean newExternalBoxEnabled = getFieldAsBoolean(newHelmRelease.getValuesSection(),
                ROOT_PROPERTIES_ALIAS, EXTENDED_SETTINGS_ALIAS, EXTERNAL_BOX_ALIAS, ENABLED_ALIAS);
        boolean oldExternalBoxEnabled = getFieldAsBoolean(oldHelmRelease.getValuesSection(),
                ROOT_PROPERTIES_ALIAS, EXTENDED_SETTINGS_ALIAS, EXTERNAL_BOX_ALIAS, ENABLED_ALIAS);
        return newExternalBoxEnabled ^ oldExternalBoxEnabled;
    }

    private static boolean containsFailingHostNetwork(HelmRelease newHelmRelease, HelmRelease oldHelmRelease) {
        boolean newHostNetworkEnabled = getFieldAsBoolean(newHelmRelease.getValuesSection(),
                ROOT_PROPERTIES_ALIAS, EXTENDED_SETTINGS_ALIAS, HOST_NETWORK);
        boolean oldHostNetworkEnabled = getFieldAsBoolean(oldHelmRelease.getValuesSection(),
                ROOT_PROPERTIES_ALIAS, EXTENDED_SETTINGS_ALIAS, HOST_NETWORK);
        return newHostNetworkEnabled ^ oldHostNetworkEnabled;
    }

    public static void generateSecretsConfig(Map<String, Object> customConfig,
                                             Map<String, String> valuesCollector,
                                             Map<String, String> pathsCollector) {
        StringSubstitutor stringSubstitutor = new StringSubstitutor(
                StringLookupFactory.INSTANCE.interpolatorStringLookup(
                        Map.of(SECRET_VALUE_PREFIX, new CustomLookupValues(valuesCollector),
                                SECRET_PATH_PREFIX, new CustomLookupPaths(pathsCollector)
                        ), null, false
                ));
        for (var entry : customConfig.entrySet()) {
            var value = entry.getValue();
            if (value instanceof String) {
                String valueStr = (String) value;
                String substituted = stringSubstitutor.replace(valueStr);
                customConfig.put(entry.getKey(), substituted);
            } else if (value instanceof Map) {
                generateSecretsConfig((Map<String, Object>) value, valuesCollector, pathsCollector);
            }
        }
    }

    public static Set<String> extractQueues(Map<String, Object> values) {
        var mqConfigObj = getFieldAsNode(values, ROOT_PROPERTIES_ALIAS, MQ_QUEUE_CONFIG_ALIAS);
        MessageRouterConfiguration mqConfig = mapper.convertValue(mqConfigObj, MessageRouterConfiguration.class);
        return mqConfig.getQueues().values()
                .stream()
                .filter(queueConfiguration -> !(queueConfiguration.getQueueName().isEmpty()))
                .map(QueueConfiguration::getQueueName).collect(Collectors.toSet());
    }

    static class CustomLookup implements StringLookup {
        private int id = 0;

        private Map<String, String> collector;

        public CustomLookup(Map<String, String> collector) {
            this.collector = collector;
        }

        @Override
        public String lookup(String key) {
            String envVarWithId = Strings.toUnderScoreUpperCase(key, id);
            id++;
            collector.put(envVarWithId, key);
            return String.format("${%s}", envVarWithId);
        }
    }

    static final class CustomLookupPaths extends CustomLookup {
        public CustomLookupPaths(Map<String, String> collector) {
            super(collector);
        }
    }

    static final class CustomLookupValues extends CustomLookup {
        public CustomLookupValues(Map<String, String> collector) {
            super(collector);
        }
    }
}
