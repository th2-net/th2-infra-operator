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

package com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.Data;

import java.util.List;


@Data
@Builder
@JsonDeserialize
public class GrpcRobinStrategy implements RoutingStrategy {

    @Builder.Default
    protected String name = "robin";

    protected List<String> endpoints;


    protected GrpcRobinStrategy() {
    }

    protected GrpcRobinStrategy(String name, List<String> endpoints) {
        this.name = name;
        this.endpoints = endpoints;
    }


    @Override
    public StrategyType getType() {
        return StrategyType.ROBIN;
    }

}
