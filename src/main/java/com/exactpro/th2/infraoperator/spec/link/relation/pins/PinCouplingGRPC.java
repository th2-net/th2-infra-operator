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

package com.exactpro.th2.infraoperator.spec.link.relation.pins;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class PinCouplingGRPC implements PinCoupling {

    private String name;
    private PinGRPC from;
    private PinGRPC to;


    public PinCouplingGRPC(@JsonProperty("name") String name,
                           @JsonProperty("from") PinGRPC from,
                           @JsonProperty("to") PinGRPC to) {
        this.name = name;
        this.from = from;
        this.to = to;
    }


    @Override
    public String getName() {
        return this.name;
    }


    @Override
    public PinGRPC getFrom() {
        return this.from;
    }


    @Override
    public PinGRPC getTo() {
        return this.to;
    }


    @Override
    public boolean equals(final Object o) {
        if (o == this)
            return true;
        if (!(o instanceof PinCouplingGRPC))
            return false;

        return Objects.equals(name, ((PinCouplingGRPC) o).name)
                && Objects.equals(from, ((PinCouplingGRPC) o).from)
                && Objects.equals(to, ((PinCouplingGRPC) o).to);
    }


    @Override
    public int hashCode() {
        throw new AssertionError("method not defined");
    }


    @Override
    public String getId() {
        return String.format("%s[%s:%s-%s:%s]",
                this.getClass().getName(),
                from.getBoxName(), from.getPinName(),
                to.getBoxName(), to.getPinName()
        );
    }


    @Override
    public String toString() {
        return String.format("name: %s from: [%s.%s] to: [%s.%s]", name, from.getBoxName(), from.getPinName(), to.getBoxName(), to.getPinName());
    }
}
