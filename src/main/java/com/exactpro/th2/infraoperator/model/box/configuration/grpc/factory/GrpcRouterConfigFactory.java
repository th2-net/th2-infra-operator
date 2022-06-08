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
import com.exactpro.th2.infraoperator.spec.shared.pin.GrpcClientPin;
import com.exactpro.th2.infraoperator.spec.shared.pin.PinSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;
import static com.exactpro.th2.infraoperator.util.HelmReleaseUtils.*;

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
     * @param resource        th2 resource containing a pins {@link PinSpec}s
     * @param grpcActiveLinks active links
     * @return ready grpc configuration based on active {@code links} and specified links in {@code resource}
     */
    public GrpcRouterConfiguration createConfig(Th2CustomResource resource, List<PinCouplingGRPC> grpcActiveLinks) {

        var boxName = extractName(resource);
        var boxNamespace = extractNamespace(resource);
        // TODO initialize as null at first
        GrpcServerConfiguration server = createServer();
        Map<String, GrpcServiceConfiguration> services = new HashMap<>();

        if (!resource.getSpec().getPins().getGrpc().getServer().isEmpty()) {
            server = createServer();
        }
        for (var pin : resource.getSpec().getPins().getGrpc().getClient()) {
            for (var link : grpcActiveLinks) {
                var toLink = link.getTo();
                var toPinName = toLink.getPinName();

                createService(pin, toPinName, boxNamespace, link, services);
            }
        }

        return new GrpcRouterConfiguration(services, server);
    }

    private void createService(GrpcClientPin currentPin, String oppositePinName, String namespace,
                               PinCouplingGRPC link, Map<String, GrpcServiceConfiguration> services) {
        PinGRPC fromBoxSpec = link.getFrom();
        String fromBoxName = fromBoxSpec.getBoxName();

        PinGRPC toBoxSpec = link.getTo();
        String toBoxName = toBoxSpec.getBoxName();

        Th2CustomResource fromBoxResource = (Th2CustomResource) OperatorState.INSTANCE
                .getResourceFromCache(fromBoxName, namespace);
        Th2CustomResource toBoxResource = (Th2CustomResource) OperatorState.INSTANCE
                .getResourceFromCache(toBoxName, namespace);

        checkForExternal(fromBoxResource, toBoxResource, toBoxSpec);

        resourceToServiceConfig(currentPin, toBoxSpec, services);

    }

    private void resourceToServiceConfig(GrpcClientPin currentPin, PinGRPC targetBoxSpec,
                                         Map<String, GrpcServiceConfiguration> services) {
        var targetBoxName = getTargetBoxName(targetBoxSpec);
        var serviceClass = currentPin.getServiceClass();
        var serviceName = currentPin.getName();
        var targetPort = getTargetBoxPort(targetBoxSpec);
        var config = services.get(serviceName);

        Map<String, GrpcEndpointConfiguration> endpoints = new HashMap<>(Map.of(
                targetBoxName + ENDPOINT_ALIAS_SUFFIX,
                new GrpcEndpointConfiguration(targetBoxName, targetPort, currentPin.getAttributes())
        ));

        if (config == null) {
            RoutingStrategy routingStrategy = new RoutingStrategy(
                    currentPin.getStrategy(),
                    new HashSet<>(endpoints.keySet())
            );
            config = new GrpcServiceConfiguration(routingStrategy, serviceClass, endpoints, currentPin.getFilters());
            services.put(serviceName, config);
        } else {
            config.getEndpoints().putAll(endpoints);

            config.getFilters().addAll(currentPin.getFilters());

            config.getStrategy().getEndpoints().addAll(new HashSet<>(endpoints.keySet()));
        }
    }

    //TODO add serviceClasses to server config
    private static GrpcServerConfiguration createServer() {
        return new GrpcServerConfiguration(
                DEFAULT_SERVER_WORKERS_COUNT, DEFAULT_PORT, null, null);
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
                Integer targetPort = grpcExternalEndpointMapping.getTargetPort();
                if (targetPort != null) {
                    targetBox.setPort(targetPort);
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
                targetBox.setPort(grpcMapping.getExposedPort());
                targetBox.setHostNetwork(true);
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
