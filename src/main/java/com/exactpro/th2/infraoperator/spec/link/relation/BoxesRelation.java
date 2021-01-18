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

package com.exactpro.th2.infraoperator.spec.link.relation;

import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCoupling;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingGRPC;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingMQ;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public final class BoxesRelation {

    private List<PinCouplingMQ> mqLinks;
    private List<PinCouplingGRPC> grpcLinks;


    private BoxesRelation(List<PinCouplingMQ> mqLinks, List<PinCouplingGRPC> grpcLinks) {

        this.mqLinks = mqLinks;
        this.grpcLinks = grpcLinks;
    }


    @JsonCreator
    public static BoxesRelation newRelation(@JsonProperty("router-mq") List<PinCouplingMQ> mqLinks,
                                            @JsonProperty("router-grpc") List<PinCouplingGRPC> grpcLinks) {

        return new BoxesRelation(mqLinks == null ? new ArrayList<>() : mqLinks,
                grpcLinks == null ? new ArrayList<>() : grpcLinks);
    }

    public static BoxesRelation newEmptyRelation() {
        return BoxesRelation.newRelation(null, null);
    }


    public List<PinCouplingMQ> getRouterMq() {
        return this.mqLinks;
    }


    public void setRouterMq(List<PinCouplingMQ> mqLinks) {
        this.mqLinks = mqLinks;
    }


    public List<PinCouplingGRPC> getRouterGrpc() {
        return this.grpcLinks;
    }


    public List<PinCoupling> getAllLinks() {
        List<PinCoupling> links = new ArrayList<>();
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
