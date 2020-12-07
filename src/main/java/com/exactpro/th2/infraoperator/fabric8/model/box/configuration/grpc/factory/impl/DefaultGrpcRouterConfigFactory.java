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

package com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc.factory.impl;

import com.exactpro.th2.infraoperator.fabric8.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc.*;
import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc.factory.GrpcRouterConfigFactory;
import com.exactpro.th2.infraoperator.fabric8.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.boxes.box.impl.BoxGrpc;
import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.boxes.bunch.impl.GrpcLinkBunch;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.FilterSpec;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.PinSpec;
import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc.GrpcEndpointMapping;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.resFinder.box.BoxResourceFinder;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.SchemaConnectionType;
import com.exactpro.th2.infraoperator.fabric8.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.fabric8.util.SchemeMappingUtils;
import com.exactpro.th2.infraoperator.fabric8.util.Strings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc.StrategyType.FILTER;
import static com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc.StrategyType.ROBIN;
import static com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils.extractNamespace;
import static com.exactpro.th2.infraoperator.fabric8.util.JsonUtils.JSON_READER;


public class DefaultGrpcRouterConfigFactory implements GrpcRouterConfigFactory {

    private static final Logger logger = LoggerFactory.getLogger(DefaultGrpcRouterConfigFactory.class);


    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_SERVER_WORKERS_COUNT = 5;
    private static final String ENDPOINT_ALIAS_SUFFIX = "-endpoint";
    private static final String SERVICE_CLASS_PLACEHOLDER = "unknown";
    private static final String EXTERNAL_BOX_ALIAS = "externalBox";
    private static final String HOST_NETWORK_ALIAS = "hostNetwork";
    private static final String SERVICE_ALIAS = "service";
    private static final String ENDPOINTS_ALIAS = "endpoints";
    private static final String GRPC_ALIAS = "grpc";
    private static final String TRUE_FLAG = "true";
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

                var fromBoxName = fromLink.getBox();
                var toBoxName = toLink.getBox();

                var fromPinName = fromLink.getPin();
                var toPinName = toLink.getPin();

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

        BoxGrpc fromBoxSpec = link.getFrom();
        String fromBoxName = fromBoxSpec.getBox();

        BoxGrpc toBoxSpec = link.getTo();
        String toBoxName = toBoxSpec.getBox();

        Th2CustomResource fromBoxResource = resourceFinder.getResource(fromBoxName, namespace);
        Th2CustomResource toBoxResource = resourceFinder.getResource(toBoxName, namespace);

        PinSpec oppositePin;
        if (fromBoxSpec.getPin().equals(currentPin.getName())) {
            oppositePin = toBoxResource.getSpec().getPin(oppositePinName);
        } else {
            oppositePin = fromBoxResource.getSpec().getPin(oppositePinName);
        }

        checkForExternal(fromBoxResource, toBoxResource, toBoxSpec);

        if (naturePinState.getFromPinName().equals(oppositePin.getName())) {
            resourceToServiceConfig(oppositePin, fromBoxSpec, services);
        } else {
            resourceToServiceConfig(oppositePin, toBoxSpec, services);
        }

    }

    private void resourceToServiceConfig(
            PinSpec targetPin,
            BoxGrpc targetBoxSpec,
            Map<String, GrpcServiceConfiguration> services
    ) {
        var targetBoxName = targetBoxSpec.isAccessedExternally() ? OperatorConfig.INSTANCE.getK8sUrl() : targetBoxSpec.getBox();

        var serviceClass = targetBoxSpec.getServiceClass();

        var serviceName = getServiceName(serviceClass);

        var targetPort = targetBoxSpec.isAccessedExternally() ? targetBoxSpec.getPort() : DEFAULT_PORT;

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
        if (link.getFrom().getPin().equals(firstPinName)) {
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

    private void checkForExternal(Th2CustomResource fromBox, Th2CustomResource toBox, BoxGrpc targetBox) {
        Map<String, Object> fromBoxSettings = fromBox.getSpec().getExtendedSettings();
        Map<String, Object> toBoxSettings = toBox.getSpec().getExtendedSettings();
        logger.debug("checking external flag for a link [from \"{}\" to \"{}\"]",
                CustomResourceUtils.annotationFor(fromBox), CustomResourceUtils.annotationFor(toBox));
        if (isExternal(fromBoxSettings) && !isExternal(toBoxSettings)) {
            logger.debug("link [from \"{}\" to \"{}\"] needs external gRPC mapping",
                    CustomResourceUtils.annotationFor(fromBox), CustomResourceUtils.annotationFor(toBox));
            GrpcEndpointMapping grpcMapping = getGrpcMapping(toBoxSettings);
            if (grpcMapping != null) {
                int nodePort = Integer.parseInt(grpcMapping.getNodePort());
                targetBox.setPort(nodePort);
                targetBox.setAccessedExternally(true);
            } else {
                logger.debug("grpcMapping for resource \"{}\" was null", CustomResourceUtils.annotationFor(toBox));
            }
        } else {
            targetBox.setAccessedExternally(false);
            logger.debug("link [from \"{}\" to \"{}\"] does NOT need external gRPC mapping",
                    CustomResourceUtils.annotationFor(fromBox), CustomResourceUtils.annotationFor(toBox));
        }
    }

    @SneakyThrows
    private GrpcEndpointMapping getGrpcMapping(Map<String, Object> boxSettings) {
        Object service = boxSettings.get(SERVICE_ALIAS);
        if (service != null) {
            JsonNode serviceJson = JSON_READER.convertValue(service, JsonNode.class);
            JsonNode endpointsJson = serviceJson.get(ENDPOINTS_ALIAS);
            if (endpointsJson != null) {
                List<GrpcEndpointMapping> grpcEndpointMappings = JSON_READER.convertValue(endpointsJson, new TypeReference<>() {
                });
                if (grpcEndpointMappings != null) {
                    for (GrpcEndpointMapping grpcEndpointMapping : grpcEndpointMappings) {
                        if (grpcEndpointMapping.getName().equals(GRPC_ALIAS)) {
                            return grpcEndpointMapping;
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isExternal(Map<String, Object> boxExtendedSettings) {
        boolean isExternal = false;
        if (boxExtendedSettings != null) {
            var hostNetwork = boxExtendedSettings.get(HOST_NETWORK_ALIAS);
            var externalBox = boxExtendedSettings.get(EXTERNAL_BOX_ALIAS);
            isExternal = (hostNetwork != null && hostNetwork.toString().equals(TRUE_FLAG));
            isExternal |= (externalBox != null && externalBox.toString().equals(TRUE_FLAG));
        }
        return isExternal;
    }

}
