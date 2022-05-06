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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Map;

@JsonDeserialize
public class GrpcRouterConfiguration {

    private Map<String, GrpcServiceConfiguration> services;

    private GrpcServerConfiguration server;

    public GrpcRouterConfiguration(Map<String, GrpcServiceConfiguration> services,
                                      GrpcServerConfiguration server) {
        this.services = services;
        this.server = server;
    }

    public Map<String, GrpcServiceConfiguration> getServices() {
        return this.services;
    }

    public GrpcServerConfiguration getServer() {
        return this.server;
    }

    public void setServices(Map<String, GrpcServiceConfiguration> services) {
        this.services = services;
    }

    public void setServer(GrpcServerConfiguration server) {
        this.server = server;
    }

    @Override
    public boolean equals(Object o) {
        throw new AssertionError("method not defined");
    }

    @Override
    public int hashCode() {
        throw new AssertionError("method not defined");
    }
}
