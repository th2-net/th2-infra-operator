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

import com.exactpro.th2.infraoperator.model.box.configuration.grpc.GrpcEndpointMapping;
import com.exactpro.th2.infraoperator.model.box.configuration.grpc.GrpcExternalEndpointMapping;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.helmRelease.HelmRelease;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.JsonUtils.JSON_READER;

public class ExtendedSettingsUtils {

    private static final String EXTENDED_SETTINGS_ALIAS = "extendedSettings";

    private static final String EXTERNAL_BOX_ALIAS = "externalBox";

    private static final String HOST_NETWORK_ALIAS = "hostNetwork";

    private static final String ENDPOINTS_ALIAS = "endpoints";

    private static final String ADDRESS_ALIAS = "address";

    private static final String ENABLED_ALIAS = "enabled";

    private static final String SERVICE_ALIAS = "service";

    private static final String GRPC_ALIAS = "grpc";

    private ExtendedSettingsUtils() {
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
                    new TypeReference<>() { });
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
                    new TypeReference<>() { });
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
}
