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

package com.exactpro.th2.infraoperator.spec.link.relation.pins;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Objects;

@JsonDeserialize
public final class PinsLinkageGRPC implements PinsLinkage {

    private String name;
    private PinGRPC from;
    private PinGRPC to;

    private PinsLinkageGRPC() {
    }

    public PinsLinkageGRPC(String name, PinGRPC from, PinGRPC to) {
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

    public boolean equals(final Object o) {
        if (o == this)
            return true;
        if (! (o instanceof PinsLinkageGRPC))
            return false;
        return Objects.equals(name, ((PinsLinkageGRPC) o).name)
                && Objects.equals(from, ((PinsLinkageGRPC) o).from)
                && Objects.equals(to, ((PinsLinkageGRPC) o).to);
    }


    public int hashCode() {
        throw new AssertionError("method not defined");
    }
}
