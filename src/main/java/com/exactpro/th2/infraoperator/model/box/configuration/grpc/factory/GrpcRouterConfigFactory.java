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

package com.exactpro.th2.infraoperator.model.box.configuration.grpc.factory;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.model.box.configuration.grpc.*;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingGRPC;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinGRPC;
import com.exactpro.th2.infraoperator.spec.shared.FilterSpec;
import com.exactpro.th2.infraoperator.spec.shared.PinSpec;
import com.exactpro.th2.infraoperator.spec.shared.SchemaConnectionType;
import com.exactpro.th2.infraoperator.util.SchemeMappingUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.model.box.configuration.grpc.StrategyType.FILTER;
import static com.exactpro.th2.infraoperator.model.box.configuration.grpc.StrategyType.ROBIN;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.HelmReleaseUtils.*;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;

/**
 * A factory that creates a grpc configuration
 * based on the th2 resource and a list of active links.
 */
public class GrpcRouterConfigFactory {

    private static final Logger logger = LoggerFactory.getLogger(GrpcRouterConfigFactory.class);

    private static final int DEFAULT_PORT = 8080;

    private static final int DEFAULT_SERVER_WORKERS_COUNT = 5;

    private static final String ENDPOINT_ALIAS_SUFFIX = "-endpoint";

    /**
     * Creates a grpc configuration based on the th2 resource and a list of active links.
     *
     * @param resource        th2 resource containing a list of {@link PinSpec}s
     * @param grpcActiveLinks active links
     * @return ready grpc configuration based on active {@code links} and specified links in {@code resource}
     */
    public GrpcRouterConfiguration createConfig(Th2CustomResource resource, List<PinCouplingGRPC> grpcActiveLinks) {

        var boxName = extractName(resource);
        var boxNamespace = extractNamespace(resource);
        // TODO initialize as null at first
        GrpcServerConfiguration server = createServer();
        Map<String, GrpcServiceConfiguration> services = new HashMap<>();

        for (var pin : resource.getSpec().getPins()) {
            if (pin.getConnectionType().equals(SchemaConnectionType.grpc_server)) {
                server = createServer();
            } else if (pin.getConnectionType().equals(SchemaConnectionType.grpc_client)) {
                for (var link : grpcActiveLinks) {
                    var fromLink = link.getFrom();
                    var fromBoxName = fromLink.getBoxName();
                    var fromPinName = fromLink.getPinName();

                    var toLink = link.getTo();
                    var toBoxName = toLink.getBoxName();
                    var toPinName = toLink.getPinName();

                    if (fromBoxName.equals(boxName) && fromPinName.equals(pin.getName())) {
                        createService(pin, toPinName, boxNamespace, link, services);
                    } else if (toBoxName.equals(boxName) && toPinName.equals(pin.getName())) {
                        createService(pin, fromPinName, boxNamespace, link, services);
                    }
                }
            }
        }

        return GrpcRouterConfiguration.builder()
                .serverConfiguration(server)
                .services(services)
                .build();
    }

    private void createService(PinSpec currentPin, String oppositePinName, String namespace,
                               PinCouplingGRPC link, Map<String, GrpcServiceConfiguration> services) {
        var naturePinState = getNaturePinState(currentPin.getName(), oppositePinName, link);

        PinGRPC fromBoxSpec = link.getFrom();
        String fromBoxName = fromBoxSpec.getBoxName();

        PinGRPC toBoxSpec = link.getTo();
        String toBoxName = toBoxSpec.getBoxName();

        Th2CustomResource fromBoxResource = (Th2CustomResource) OperatorState.INSTANCE
                .getResourceFromCache(fromBoxName, namespace);
        Th2CustomResource toBoxResource = (Th2CustomResource) OperatorState.INSTANCE
                .getResourceFromCache(toBoxName, namespace);

        PinSpec oppositePin;
        if (fromBoxSpec.getPinName().equals(currentPin.getName())) {
            oppositePin = toBoxResource.getSpec().getPin(oppositePinName);
        } else {
            oppositePin = fromBoxResource.getSpec().getPin(oppositePinName);
        }

        checkForExternal(fromBoxResource, toBoxResource, toBoxSpec);

        if (naturePinState.getFromPinName().equals(oppositePin.getName())) {
            resourceToServiceConfig(currentPin, oppositePin, fromBoxSpec, services);
        } else {
            resourceToServiceConfig(currentPin, oppositePin, toBoxSpec, services);
        }

    }

    private void resourceToServiceConfig(PinSpec currentPin, PinSpec targetPin, PinGRPC targetBoxSpec,
                                         Map<String, GrpcServiceConfiguration> services) {
        var targetBoxName = getTargetBoxName(targetBoxSpec);
        var serviceClass = currentPin.getServiceClass();
        var serviceName = currentPin.getName();
        var targetPort = getTargetBoxPort(targetBoxSpec);
        var config = services.get(serviceName);

        Map<String, GrpcEndpointConfiguration> endpoints = new HashMap<>(Map.of(
                targetBoxName + ENDPOINT_ALIAS_SUFFIX, GrpcEndpointConfiguration.builder()
                        .host(targetBoxName)
                        .port(targetPort)
                        .attributes(currentPin.getAttributes())
                        .build()
        ));

        if (config == null) {

            config = GrpcServiceConfiguration.builder()
                    .serviceClass(serviceClass)
                    .endpoints(endpoints)
                    .build();

            var strategy = currentPin.getStrategy();

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

    private NaturePinState getNaturePinState(String firstPinName, String secondPinName, PinCouplingGRPC link) {
        if (link.getFrom().getPinName().equals(firstPinName)) {
            return new NaturePinState(firstPinName, secondPinName);
        }
        return new NaturePinState(secondPinName, firstPinName);
    }

    private List<GrpcRouterFilterConfiguration> schemeFiltersToGrpcFilters(String targetHostName,
                                                                           Set<FilterSpec> filterSpecs) {
        return filterSpecs.stream()
                .map(filterSpec ->
                        new GrpcRouterFilterConfiguration(
                                targetHostName,
                                SchemeMappingUtils.specToConfigFieldFiltersNew(filterSpec.getMetadataFilter()),
                                SchemeMappingUtils.specToConfigFieldFiltersNew(filterSpec.getMessageFilter())
                        )
                ).collect(Collectors.toList());
    }

    @Data
    @AllArgsConstructor
    private static class NaturePinState {
        private String fromPinName;

        private String toPinName;
    }

    //TODO add serviceClasses to server config
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

    private void checkForExternal(Th2CustomResource fromBox, Th2CustomResource toBox, PinGRPC targetBox) {
        Map<String, Object> fromBoxSettings = fromBox.getSpec().getExtendedSettings();
        Map<String, Object> toBoxSettings = toBox.getSpec().getExtendedSettings();
        logger.debug("checking externalBox or hostNetwork flags for a link [from \"{}\" to \"{}\"]",
                annotationFor(fromBox), annotationFor(toBox));
        if (isExternalBox(toBoxSettings)) {
            logger.debug("link [from \"{}\" to \"{}\"] needs externalBox gRPC mapping",
                    annotationFor(fromBox), annotationFor(toBox));
            GrpcExternalEndpointMapping grpcExternalEndpointMapping = getGrpcExternalMapping(toBoxSettings);
            if (grpcExternalEndpointMapping != null) {
                String targetPort = grpcExternalEndpointMapping.getTargetPort();
                if (targetPort != null) {
                    targetBox.setPort(Integer.parseInt(targetPort));
                    targetBox.setExternalHost(getExternalHost(toBoxSettings));
                    targetBox.setExternalBox(true);
                } else {
                    logger.warn("targetPort for resource \"{}\" was null", annotationFor(toBox));
                    externalBoxEndpointNotFound(toBox);
                }
            } else {
                logger.warn("grpcMapping for resource \"{}\" was null", annotationFor(toBox));
                externalBoxEndpointNotFound(toBox);
            }
        } else if (isHostNetwork(fromBoxSettings) || isHostNetwork(toBoxSettings) || isExternalBox(fromBoxSettings)) {
            logger.debug("link [from \"{}\" to \"{}\"] needs hostNetwork gRPC mapping",
                    annotationFor(fromBox), annotationFor(toBox));
            GrpcEndpointMapping grpcMapping = getGrpcMapping(toBoxSettings);
            if (grpcMapping != null) {
                String nodePort = grpcMapping.getNodePort();
                if (nodePort != null) {
                    targetBox.setPort(Integer.parseInt(nodePort));
                    targetBox.setHostNetwork(true);
                } else {
                    logger.warn("nodePort for resource \"{}\" was null", annotationFor(toBox));
                    hostNetworkEndpointNotFound(toBox);
                }
            } else {
                logger.warn("grpcMapping for resource \"{}\" was null", annotationFor(toBox));
                hostNetworkEndpointNotFound(toBox);
            }
        } else {
            targetBox.setHostNetwork(false);
            targetBox.setExternalBox(false);
            logger.debug("link [from \"{}\" to \"{}\"] does NOT need external gRPC mapping",
                    annotationFor(fromBox), annotationFor(toBox));
        }
    }
}
