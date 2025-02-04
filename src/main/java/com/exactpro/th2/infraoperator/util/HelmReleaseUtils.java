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

import com.exactpro.th2.infraoperator.model.box.dictionary.DictionaryEntity;
import com.exactpro.th2.infraoperator.model.box.grpc.GrpcEndpointMapping;
import com.exactpro.th2.infraoperator.model.box.grpc.GrpcExternalEndpointMapping;
import com.exactpro.th2.infraoperator.model.box.mq.MessageRouterConfiguration;
import com.exactpro.th2.infraoperator.model.box.mq.QueueConfiguration;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.Th2Spec;
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease;
import com.exactpro.th2.infraoperator.spec.helmrelease.InstantiableMap;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookupFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.DICTIONARIES_ALIAS;
import static com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.ENABLED_ALIAS;
import static com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.EXTENDED_SETTINGS_ALIAS;
import static com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.EXTERNAL_BOX_ALIAS;
import static com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.MQ_QUEUE_CONFIG_ALIAS;
import static com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.SERVICE_ALIAS;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.JsonUtils.JSON_MAPPER;
import static com.exactpro.th2.infraoperator.util.JsonUtils.YAML_MAPPER;

public class HelmReleaseUtils {
    private static final Logger logger = LoggerFactory.getLogger(HelmReleaseUtils.class);

    private static final String HOST_NETWORK_ALIAS = "hostNetwork";

    private static final String NODE_PORT_ALIAS = "nodePort";

    private static final String ENDPOINTS_ALIAS = "endpoints";

    private static final String ADDRESS_ALIAS = "address";

    private static final String GRPC_ALIAS = "grpc";

    private static final String SECRET_VALUE_PREFIX = "secret_value";

    private static final String SECRET_PATH_PREFIX = "secret_path";

    private static final String DICTIONARY_LINK_PREFIX = "dictionary_link";

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
        JsonNode endpointsNode = getFieldAsNode(boxSettings, SERVICE_ALIAS, NODE_PORT_ALIAS);
        if (endpointsNode != null) {
            List<GrpcEndpointMapping> grpcEndpointMappings = JSON_MAPPER.convertValue(endpointsNode,
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
            List<GrpcExternalEndpointMapping> externalEndpoints = JSON_MAPPER.convertValue(endpointsNode,
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
            return JSON_MAPPER.convertValue(address, String.class);
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
        JsonNode currentField = JSON_MAPPER.convertValue(sourceObj, JsonNode.class);
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
        JsonNode currentField = JSON_MAPPER.convertValue(sourceObj, JsonNode.class);
        for (String field : fields) {
            currentField = currentField.get(field);
            if (currentField == null) {
                return defaultValue;
            }
        }
        return JSON_MAPPER.convertValue(currentField, Boolean.class);
    }

    private static Map<String, Object> getSectionReference(Map<String, Object> componentValues, String... fields) {
        Map<String, Object> currentSection = (Map<String, Object>) componentValues.get(fields[0]);
        for (int i = 1; i < fields.length; i++) {
            if (currentSection != null) {
                currentSection = (Map<String, Object>) currentSection.get(fields[i]);
            }
        }
        return currentSection;
    }

    public static Map<String, Object> extractConfigSection(HelmRelease helmRelease, String key) {
        return (Map<String, Object>) helmRelease.getComponentValuesSection().get(key);
    }

    public static String extractComponentName(HelmRelease helmRelease) {
        return (String) helmRelease.getComponentValuesSection().get("name");
    }

    public static Collection<DictionaryEntity> extractDictionariesConfig(HelmRelease helmRelease) {
        return (Collection<DictionaryEntity>) helmRelease.getComponentValuesSection().get(DICTIONARIES_ALIAS);
    }

    public static void generateSecretsConfig(Th2Spec spec,
                                             Map<String, String> valuesCollector,
                                             Map<String, String> pathsCollector) {
        StringSubstitutor stringSubstitutor = new StringSubstitutor(
                StringLookupFactory.INSTANCE.interpolatorStringLookup(
                        Map.of(SECRET_VALUE_PREFIX, new Strings.CustomLookupForSecrets(valuesCollector),
                                SECRET_PATH_PREFIX, new Strings.CustomLookupForSecrets(pathsCollector)
                        ), null, false
                ));
        try {
            String customConfigStr = YAML_MAPPER.writeValueAsString(spec.getCustomConfig());
            spec.setCustomConfig(YAML_MAPPER.readValue(stringSubstitutor.replace(customConfigStr),
                    new TypeReference<>() {
                    }));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static void generateDictionariesConfig(Th2Spec spec,
                                                  Set<DictionaryEntity> dictionariesCollector) {
        StringSubstitutor stringSubstitutor = new StringSubstitutor(
                StringLookupFactory.INSTANCE.interpolatorStringLookup(
                        Map.of(DICTIONARY_LINK_PREFIX, new Strings.CustomLookupForDictionaries(dictionariesCollector)
                        ), null, false
                ));
        try {
            String customConfigStr = YAML_MAPPER.writeValueAsString(spec.getCustomConfig());
            spec.setCustomConfig(YAML_MAPPER.readValue(stringSubstitutor.replace(customConfigStr),
                    new TypeReference<>() {
                    }));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static Set<String> extractQueues(Map<String, Object> componentValues) {
        MessageRouterConfiguration mqConfig = JSON_MAPPER.convertValue(
                componentValues.get(MQ_QUEUE_CONFIG_ALIAS),
                MessageRouterConfiguration.class
        );
        return mqConfig.getQueues().values()
                .stream()
                .filter(queueConfiguration -> !(queueConfiguration.getQueueName().isEmpty()))
                .map(QueueConfiguration::getQueueName).collect(Collectors.toSet());
    }

    public static boolean needsToBeDeleted(HelmRelease newHelmRelease, HelmRelease oldHelmRelease) {
        if (oldHelmRelease == null) {
            return false;
        }
        return isAlreadyFailed(oldHelmRelease);
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
}
