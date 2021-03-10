package com.exactpro.th2.infraoperator.util;

import com.exactpro.th2.infraoperator.model.box.configuration.grpc.GrpcEndpointMapping;
import com.exactpro.th2.infraoperator.model.box.configuration.grpc.GrpcExternalEndpointMapping;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.ConfigNotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.JsonUtils.JSON_READER;

public class ExtendedSettingsUtils {
    private static final String SPLIT_CHARACTER = "\\.";
    private static final String SEPARATOR = ".";

    private static final String EXTENDED_SETTINGS_ALIAS = "extended-settings";
    private static final String EXTERNAL_BOX_ALIAS = "externalBox";
    private static final String HOST_NETWORK_ALIAS = "hostNetwork";
    private static final String SERVICE_ALIAS = "service";
    private static final String ENDPOINTS_ALIAS = "endpoints";
    private static final String GRPC_ALIAS = "grpc";
    private static final String ENABLED_ALIAS = "enabled";
    private static final String ADDRESS_ALIAS = "address";

    private ExtendedSettingsUtils() {
    }

    public static boolean isHostNetwork(Map<String, Object> boxExtendedSettings) {
        return boxExtendedSettings != null && getFieldAsBoolean(boxExtendedSettings, HOST_NETWORK_ALIAS);
    }

    public static boolean isExternalBox(Map<String, Object> boxExtendedSettings) {
        String path = EXTERNAL_BOX_ALIAS + SEPARATOR + ENABLED_ALIAS;
        return boxExtendedSettings != null && getFieldAsBoolean(boxExtendedSettings, path);
    }

    public static GrpcEndpointMapping getGrpcMapping(Map<String, Object> boxSettings) {
        if (boxSettings == null) {
            return null;
        }
        String path = SERVICE_ALIAS + SEPARATOR + ENDPOINTS_ALIAS;
        JsonNode endpointsNode = getFieldAsNode(boxSettings, path);
        if (endpointsNode != null) {
            List<GrpcEndpointMapping> grpcEndpointMappings = JSON_READER.convertValue(endpointsNode, new TypeReference<>() {
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
        String path = EXTERNAL_BOX_ALIAS + SEPARATOR + ENDPOINTS_ALIAS;
        JsonNode endpointsNode = getFieldAsNode(boxSettings, path);
        if (endpointsNode != null) {
            List<GrpcExternalEndpointMapping> externalEndpoints = JSON_READER.convertValue(endpointsNode, new TypeReference<>() {
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
        String path = EXTERNAL_BOX_ALIAS + SEPARATOR + ADDRESS_ALIAS;
        JsonNode address = getFieldAsNode(boxSettings, path);
        if (address != null) {
            return JSON_READER.convertValue(address, String.class);
        }
        return null;
    }

    public static void hostNetworkEndpointNotFound(Th2CustomResource box) throws ConfigNotFoundException {
        String message = String.format(
                "Could not find HostNetworkEndpoint configuration for [%S], please check '%s' section in CR",
                annotationFor(box), EXTENDED_SETTINGS_ALIAS + SEPARATOR + SERVICE_ALIAS + SEPARATOR + ENDPOINTS_ALIAS);
        throw new ConfigNotFoundException(message);
    }

    public static void externalBoxEndpointNotFound(Th2CustomResource box) throws ConfigNotFoundException {
        String message = String.format(
                "Could not find ExternalBoxEndpoint configuration for [%S], please check '%s' section in CR",
                annotationFor(box), EXTENDED_SETTINGS_ALIAS + SEPARATOR + EXTERNAL_BOX_ALIAS + SEPARATOR + ENDPOINTS_ALIAS);
        throw new ConfigNotFoundException(message);
    }

    private static JsonNode getFieldAsNode(Object sourceObj, String path) {
        String[] fields = path.split(SPLIT_CHARACTER);
        JsonNode currentField = JSON_READER.convertValue(sourceObj, JsonNode.class);
        for (String field : fields) {
            currentField = currentField.get(field);
            if (currentField == null) {
                return null;
            }
        }
        return currentField;
    }

    private static boolean getFieldAsBoolean(Object sourceObj, String path) {
        return getFieldAsBoolean(sourceObj, path, false);
    }

    private static boolean getFieldAsBoolean(Object sourceObj, String path, boolean defaultValue) {
        String[] fields = path.split(SPLIT_CHARACTER);
        JsonNode currentField = JSON_READER.convertValue(sourceObj, JsonNode.class);
        for (String field : fields) {
            currentField = currentField.get(field);
            if (currentField == null) {
                return defaultValue;
            }
        }
        return JSON_READER.convertValue(currentField, Boolean.class);
    }

    private static Map<String, Object> getSectionReference(Map<String, Object> extendedSettings, String... fields) {
        Map<String, Object> currentSection = (Map<String, Object>) extendedSettings.get(fields[0]);
        for (int i = 1; i < fields.length; i++) {
            currentSection = (Map<String, Object>) currentSection.get(fields[i]);
        }
        return currentSection;
    }

    public static <R> void convertServiceEnabled(Map<String, Object> extendedSettings, Function<String, R> converter) {
        Map<String, Object> service = getSectionReference(extendedSettings, SERVICE_ALIAS);
        if (service != null) {
            var currentValue = service.get(ENABLED_ALIAS);
            if (currentValue != null) {
                service.put(ENABLED_ALIAS, converter.apply(currentValue.toString()));
            }
        }
    }

    public static <R> void convertExternalBoxEnabled(Map<String, Object> extendedSettings, Function<String, R> converter) {
        Map<String, Object> service = getSectionReference(extendedSettings, EXTERNAL_BOX_ALIAS);
        if (service != null) {
            var currentValue = service.get(ENABLED_ALIAS);
            if (currentValue != null) {
                service.put(ENABLED_ALIAS, converter.apply(currentValue.toString()));
            }
        }
    }
}
