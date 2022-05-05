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

package com.exactpro.th2.infraoperator.model.box.configuration.grpc;

import java.util.List;
import java.util.Map;

public class GrpcServiceConfiguration {

    private final RoutingStrategy strategy;

    private final String serviceClass;

    private final Map<String, GrpcEndpointConfiguration> endpoints;

    private final List<Object> filters;

    public GrpcServiceConfiguration(RoutingStrategy strategy, String serviceClass,
                                       Map<String, GrpcEndpointConfiguration> endpoints,
                                       List<Object> filters) {
        this.strategy = strategy;
        this.serviceClass = serviceClass;
        this.endpoints = endpoints;
        this.filters = filters;
    }

    public RoutingStrategy getStrategy() {
        return strategy;
    }

    public String getServiceClass() {
        return serviceClass;
    }

    public Map<String, GrpcEndpointConfiguration> getEndpoints() {
        return endpoints;
    }

    public List<Object> getFilters() {
        return filters;
    }
}
