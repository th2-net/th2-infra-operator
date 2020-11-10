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

import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc.*;
import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc.factory.GrpcRouterConfigFactory;
import com.exactpro.th2.infraoperator.fabric8.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.boxes.box.impl.BoxGrpc;
import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.boxes.bunch.impl.GrpcLinkBunch;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.FilterSpec;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.PinSpec;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.resFinder.box.BoxResourceFinder;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.SchemaConnectionType;
import com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils;
import com.exactpro.th2.infraoperator.fabric8.util.SchemeMappingUtils;
import com.exactpro.th2.infraoperator.fabric8.util.Strings;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc.StrategyType.FILTER;
import static com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc.StrategyType.ROBIN;


public class DefaultGrpcRouterConfigFactory implements GrpcRouterConfigFactory {

    private static final Logger logger = LoggerFactory.getLogger(DefaultGrpcRouterConfigFactory.class);


    private static final int DEFAULT_PORT = 8080;

    private static final int DEFAULT_SERVER_WORKERS_COUNT = 5;

    private static final String ENDPOINT_ALIAS_SUFFIX = "-endpoint";

    private static final String SERVICE_CLASS_PLACEHOLDER = "unknown";

    private static final GrpcServerConfiguration DEFAULT_SERVER = createServer();

    private final BoxResourceFinder resourceFinder;


    public DefaultGrpcRouterConfigFactory(BoxResourceFinder resourceFinder) {
        this.resourceFinder = resourceFinder;
    }


    @Override
    public GrpcRouterConfiguration createConfig(Th2CustomResource resource, List<GrpcLinkBunch> grpcActiveLinks) {

        var boxName = ExtractUtils.extractName(resource);

        var boxNamespace = ExtractUtils.extractNamespace(resource);

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


        var fromBoxSpec = link.getFrom();

        var fromBoxName = fromBoxSpec.getBox();

        var toBoxSpec = link.getTo();

        var toBoxName = toBoxSpec.getBox();


        PinSpec oppositePin;
        if (fromBoxSpec.getPin().equals(currentPin.getName())) {
            oppositePin = resourceFinder.getResource(toBoxName, namespace).getSpec().getPin(oppositePinName);
        } else {
            oppositePin = resourceFinder.getResource(fromBoxName, namespace).getSpec().getPin(oppositePinName);
        }

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
        var targetBoxName = targetBoxSpec.getBox();

        var serviceClass = targetBoxSpec.getServiceClass();

        var serviceName = getServiceName(serviceClass);

        var config = services.get(serviceName);

        Map<String, GrpcEndpointConfiguration> endpoints = new HashMap<>(Map.of(
                targetBoxName + ENDPOINT_ALIAS_SUFFIX, GrpcEndpointConfiguration.builder()
                        .host(targetBoxName)
                        .port(DEFAULT_PORT)
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

}
