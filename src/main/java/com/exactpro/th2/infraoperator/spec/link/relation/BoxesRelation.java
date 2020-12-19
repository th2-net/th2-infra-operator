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

package com.exactpro.th2.infraoperator.spec.link.relation;

import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinsLinkage;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinsLinkageGRPC;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinsLinkageMQ;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
import java.util.List;

@JsonDeserialize
public final class BoxesRelation {

    private List<PinsLinkageMQ> mqLinks;
    private List<PinsLinkageGRPC> grpcLinks;


    public BoxesRelation(@JsonProperty("router-mq") List<PinsLinkageMQ> mqLinks
            , @JsonProperty("router-grpc") List<PinsLinkageGRPC> grpcLinks) {

        this.mqLinks = mqLinks == null ? new ArrayList<>() : mqLinks;
        this.grpcLinks = grpcLinks == null ? new ArrayList<>() : grpcLinks;
    }


    public static BoxesRelation newEmptyRelation() {
        return new BoxesRelation(null, null);
    }


    public List<PinsLinkageMQ> getRouterMq() {
        return this.mqLinks;
    }


    public void setRouterMq(List<PinsLinkageMQ> mqLinks) {
        this.mqLinks = mqLinks;
    }


    public List<PinsLinkageGRPC> getRouterGrpc() {
        return this.grpcLinks;
    }


    public List<PinsLinkage> getAllLinks() {
        List<PinsLinkage> links = new ArrayList<>();
        if (mqLinks != null)
            links.addAll(mqLinks);
        if (grpcLinks != null)
            links.addAll(grpcLinks);
        return links;
    }


    @Override
    public boolean equals(final Object o) {
        throw new AssertionError("method not defined");
    }


    @Override
    public int hashCode() {
        throw new AssertionError("method not defined");
    }
}
