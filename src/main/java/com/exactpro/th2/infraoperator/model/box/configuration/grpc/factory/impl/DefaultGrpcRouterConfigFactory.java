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

package com.exactpro.th2.infraoperator.model.box.configuration.grpc.factory.impl;

import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.model.box.configuration.grpc.*;
import com.exactpro.th2.infraoperator.model.box.configuration.grpc.factory.GrpcRouterConfigFactory;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinGRPC;
import com.exactpro.th2.infraoperator.spec.link.relation.boxes.bunch.impl.GrpcLinkBunch;
import com.exactpro.th2.infraoperator.spec.shared.FilterSpec;
import com.exactpro.th2.infraoperator.spec.shared.PinSpec;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.ConfigNotFoundException;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.box.BoxResourceFinder;
import com.exactpro.th2.infraoperator.spec.shared.SchemaConnectionType;
import com.exactpro.th2.infraoperator.util.SchemeMappingUtils;
import com.exactpro.th2.infraoperator.util.Strings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.model.box.configuration.grpc.StrategyType.FILTER;
import static com.exactpro.th2.infraoperator.model.box.configuration.grpc.StrategyType.ROBIN;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;
import static com.exactpro.th2.infraoperator.util.JsonUtils.JSON_READER;


public class DefaultGrpcRouterConfigFactory implements GrpcRouterConfigFactory {

    private static final Logger logger = LoggerFactory.getLogger(DefaultGrpcRouterConfigFactory.class);

    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_SERVER_WORKERS_COUNT = 5;
    private static final String ENDPOINT_ALIAS_SUFFIX = "-endpoint";
    private static final String SERVICE_CLASS_PLACEHOLDER = "unknown";
    private static final String EXTENDED_SETTINGS_ALIAS = "extended-settings";
    private static final String EXTERNAL_BOX_ALIAS = "externalBox";
    private static final String HOST_NETWORK_ALIAS = "hostNetwork";
    private static final String SERVICE_ALIAS = "service";
    private static final String ENDPOINTS_ALIAS = "endpoints";
    private static final String GRPC_ALIAS = "grpc";
    private static final String ENABLED_ALIAS = "enabled";
    private static final String ADDRESS_ALIAS = "address";
    private static final String SEPARATOR = ".";
    private static final String SPLIT_CHARACTER = "\\.";
    private static final GrpcServerConfiguration DEFAULT_SERVER = createServer();
    private final BoxResourceFinder resourceFinder;


    public DefaultGrpcRouterConfigFactory(BoxResourceFinder resourceFinder) {
        this.resourceFinder = resourceFinder;
    }


    @Override
    public GrpcRouterConfiguration createConfig(Th2CustomResource resource, List<GrpcLinkBunch> grpcActiveLinks) {

        var boxName = extractName(resource);
        var boxNamespace = extractNamespace(resource);
        Map<String, GrpcServiceConfiguration> services = new HashMap<>();

        for (var pin : resource.getSpec().getPins()) {
            if (!pin.getConnectionType().equals(SchemaConnectionType.grpc)) {
                continue;
            }

            for (var link : grpcActiveLinks) {
                var fromLink = link.getFrom();
                var toLink = link.getTo();

                var fromBoxName = fromLink.getBoxName();
                var toBoxName = toLink.getBoxName();

                var fromPinName = fromLink.getPinName();
                var toPinName = toLink.getPinName();

                if (fromBoxName.equals(boxName) && fromPinName.equals(pin.getName())) {
                    createService(pin, toPinName, boxNamespace, link, services);
                } else if (toBoxName.equals(boxName) && toPinName.equals(pin.getName())) {
                    createService(pin, fromPinName, boxNamespace, link, services);
                }
            }
        }

        return GrpcRouterConfiguration.builder()
                .serverConfiguration(DEFAULT_SERVER)
                .services(services)
                .build();
    }


    private void createService(
            PinSpec currentPin,
            String oppositePinName,
            String namespace,
            GrpcLinkBunch link,
            Map<String, GrpcServiceConfiguration> services
    ) {
        var naturePinState = getNaturePinState(currentPin.getName(), oppositePinName, link);

        PinGRPC fromBoxSpec = link.getFrom();
        String fromBoxName = fromBoxSpec.getBoxName();

        PinGRPC toBoxSpec = link.getTo();
        String toBoxName = toBoxSpec.getBoxName();

        Th2CustomResource fromBoxResource = resourceFinder.getResource(fromBoxName, namespace);
        Th2CustomResource toBoxResource = resourceFinder.getResource(toBoxName, namespace);

        PinSpec oppositePin;
        if (fromBoxSpec.getPinName().equals(currentPin.getName())) {
            oppositePin = toBoxResource.getSpec().getPin(oppositePinName);
        } else {
            oppositePin = fromBoxResource.getSpec().getPin(oppositePinName);
        }

        try {
            checkForExternal(fromBoxResource, toBoxResource, toBoxSpec);

            if (naturePinState.getFromPinName().equals(oppositePin.getName())) {
                resourceToServiceConfig(oppositePin, fromBoxSpec, services);
            } else {
                resourceToServiceConfig(oppositePin, toBoxSpec, services);
            }
        } catch (ConfigNotFoundException e) {
            logger.error(e.getMessage(), e);
        }

    }

    private void resourceToServiceConfig(
            PinSpec targetPin,
            PinGRPC targetBoxSpec,
            Map<String, GrpcServiceConfiguration> services
    ) {
        var targetBoxName = getTargetBoxName(targetBoxSpec);
        var serviceClass = targetBoxSpec.getServiceClass();
        var serviceName = getServiceName(serviceClass);
        var targetPort = getTargetBoxPort(targetBoxSpec);
        var config = services.get(serviceName);

        Map<String, GrpcEndpointConfiguration> endpoints = new HashMap<>(Map.of(
                targetBoxName + ENDPOINT_ALIAS_SUFFIX, GrpcEndpointConfiguration.builder()
                        .host(targetBoxName)
                        .port(targetPort)
                        .build()
        ));

        if (Objects.isNull(config)) {

            config = GrpcServiceConfiguration.builder()
                    .serviceClass(serviceClass)
                    .endpoints(endpoints)
                    .build();

            var strategy = targetBoxSpec.getStrategy();

            if (strategy.equals(ROBIN.getActualName())) {
                config.setStrategy(GrpcRobinStrategy.builder()
                        .endpoints(new ArrayList<>(endpoints.keySet()))
                        .build());
            } else if (strategy.equals(FILTER.getActualName())) {
                config.setStrategy(GrpcFilterStrategy.builder()
                        .filters(schemeFiltersToGrpcFilters(targetBoxName, targetPin.getFilters()))
                        .build());
            } else {
                // should never happen if the existing list of strategies has not been expanded
                throw new RuntimeException("Unknown routing strategy '" + strategy + "'");
            }

            services.put(serviceName, config);

        } else {
            var strategy = config.getStrategy();

            config.getEndpoints().putAll(endpoints);

            if (strategy.getType().equals(ROBIN)) {
                var robinStrategy = (GrpcRobinStrategy) strategy;
                robinStrategy.getEndpoints().addAll(endpoints.keySet());
            } else if (strategy.getType().equals(FILTER)) {
                var filterStrategy = (GrpcFilterStrategy) strategy;
                filterStrategy.getFilters().addAll(schemeFiltersToGrpcFilters(targetBoxName, targetPin.getFilters()));
            }
        }
    }


    private String getServiceName(String serviceClass) {
        if (Strings.isNullOrEmpty(serviceClass)) {
            return SERVICE_CLASS_PLACEHOLDER;
        }

        var classParts = serviceClass.split("\\.");

        return StringUtils.uncapitalize(classParts[classParts.length - 1]);
    }

    private NaturePinState getNaturePinState(String firstPinName, String secondPinName, GrpcLinkBunch link) {
        if (link.getFrom().getPinName().equals(firstPinName)) {
            return new NaturePinState(firstPinName, secondPinName);
        }
        return new NaturePinState(secondPinName, firstPinName);
    }

    private List<GrpcRouterFilterConfiguration> schemeFiltersToGrpcFilters(String targetHostName, Set<FilterSpec> filterSpecs) {
        return filterSpecs.stream()
                .map(filterSpec ->
                        GrpcRouterFilterConfiguration.builder()
                                .endpoint(targetHostName)
                                .metadata(SchemeMappingUtils.specToConfigFieldFilters(filterSpec.getMetadataFilter()))
                                .message(SchemeMappingUtils.specToConfigFieldFilters(filterSpec.getMessageFilter()))
                                .build()
                ).collect(Collectors.toList());
    }


    @Data
    @AllArgsConstructor
    private static class NaturePinState {
        private String fromPinName;
        private String toPinName;
    }

    private static GrpcServerConfiguration createServer() {
        return GrpcServerConfiguration.builder()
                .workers(DEFAULT_SERVER_WORKERS_COUNT)
                .port(DEFAULT_PORT)
                .build();
    }

    private String getTargetBoxName(PinGRPC targetBox) {
        if (targetBox.isHostNetwork()) {
            return OperatorConfig.INSTANCE.getK8sUrl();
        } else if (targetBox.isExternalBox()) {
            return targetBox.getExternalHost();
        }
        return targetBox.getBoxName();
    }

    private int getTargetBoxPort(PinGRPC targetBox) {
        if (targetBox.isHostNetwork() || targetBox.isExternalBox()) {
            return targetBox.getPort();
        }
        return DEFAULT_PORT;
    }

    private void checkForExternal(Th2CustomResource fromBox, Th2CustomResource toBox, PinGRPC targetBox) throws ConfigNotFoundException {
        Map<String, Object> fromBoxSettings = fromBox.getSpec().getExtendedSettings();
        Map<String, Object> toBoxSettings = toBox.getSpec().getExtendedSettings();
        logger.debug("checking externalBox or hostNetwork flags for a link [from \"{}\" to \"{}\"]", annotationFor(fromBox), annotationFor(toBox));
        if (isExternalBox(toBoxSettings)) {
            logger.debug("link [from \"{}\" to \"{}\"] needs externalBox gRPC mapping", annotationFor(fromBox), annotationFor(toBox));
            GrpcExternalEndpointMapping grpcExternalEndpointMapping = getGrpcExternalMapping(toBoxSettings);
            if (grpcExternalEndpointMapping != null) {
                targetBox.setPort(Integer.parseInt(grpcExternalEndpointMapping.getTargetPort()));
                targetBox.setExternalHost(getExternalHost(toBoxSettings));
                targetBox.setExternalBox(true);
            } else {
                logger.debug("grpcMapping for resource \"{}\" was null", annotationFor(toBox));
                externalBoxEndpointNotFound(toBox);
            }
        } else if (isHostNetwork(fromBoxSettings) || isHostNetwork(toBoxSettings) || isExternalBox(fromBoxSettings)) {
            logger.debug("link [from \"{}\" to \"{}\"] needs hostNetwork gRPC mapping", annotationFor(fromBox), annotationFor(toBox));
            GrpcEndpointMapping grpcMapping = getGrpcMapping(toBoxSettings);
            if (grpcMapping != null) {
                targetBox.setPort(Integer.parseInt(grpcMapping.getNodePort()));
                targetBox.setHostNetwork(true);
            } else {
                logger.debug("grpcMapping for resource \"{}\" was null", annotationFor(toBox));
                hostNetworkEndpointNotFound(toBox);
            }
        } else {
            targetBox.setHostNetwork(false);
            targetBox.setExternalBox(false);
            logger.debug("link [from \"{}\" to \"{}\"] does NOT need external gRPC mapping", annotationFor(fromBox), annotationFor(toBox));
        }
    }

    private boolean isHostNetwork(Map<String, Object> boxExtendedSettings) {
        return boxExtendedSettings != null && getFieldAsBoolean(boxExtendedSettings, HOST_NETWORK_ALIAS);
    }

    private boolean isExternalBox(Map<String, Object> boxExtendedSettings) {
        String path = EXTERNAL_BOX_ALIAS + SEPARATOR + ENABLED_ALIAS;
        return boxExtendedSettings != null && getFieldAsBoolean(boxExtendedSettings, path);
    }

    private GrpcEndpointMapping getGrpcMapping(Map<String, Object> boxSettings) {
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

    private GrpcExternalEndpointMapping getGrpcExternalMapping(Map<String, Object> boxSettings) {
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

    private String getExternalHost(Map<String, Object> boxSettings) {
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

    private JsonNode getFieldAsNode(Object sourceObj, String path) {
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

    private boolean getFieldAsBoolean(Object sourceObj, String path) {
        return getFieldAsBoolean(sourceObj, path, false);
    }

    private boolean getFieldAsBoolean(Object sourceObj, String path, boolean defaultValue) {
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

    private void hostNetworkEndpointNotFound(Th2CustomResource box) throws ConfigNotFoundException {
        String message = String.format(
                "Could not find HostNetworkEndpoint configuration for [%S], please check '%s' section in CR",
                annotationFor(box), EXTENDED_SETTINGS_ALIAS + SEPARATOR + SERVICE_ALIAS + SEPARATOR + ENDPOINTS_ALIAS);
        throw new ConfigNotFoundException(message);
    }

    private void externalBoxEndpointNotFound(Th2CustomResource box) throws ConfigNotFoundException {
        String message = String.format(
                "Could not find ExternalBoxEndpoint configuration for [%S], please check '%s' section in CR",
                annotationFor(box), EXTENDED_SETTINGS_ALIAS + SEPARATOR + EXTERNAL_BOX_ALIAS + SEPARATOR + ENDPOINTS_ALIAS);
        throw new ConfigNotFoundException(message);
    }
}
