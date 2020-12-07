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

package com.exactpro.th2.infraoperator.fabric8.spec.link.relation.boxes.box.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@JsonDeserialize
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class BoxGrpc extends BoxMq {

    @JsonProperty("service-class")
    private String serviceClass;

    private String strategy;

    private boolean isAccessedExternally;

    private int port;

    protected BoxGrpc() {
    }

    protected BoxGrpc(String serviceClass, String strategy) {
        this.serviceClass = serviceClass;
        this.strategy = strategy;
    }

    protected BoxGrpc(String box, String pin, String serviceClass, String strategy) {
        super(box, pin);
        this.serviceClass = serviceClass;
        this.strategy = strategy;
    }
}
