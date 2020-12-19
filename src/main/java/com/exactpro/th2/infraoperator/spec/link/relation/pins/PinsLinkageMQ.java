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
public final class PinsLinkageMQ implements PinsLinkage {

    private String name;
    private PinMQ from;
    private PinMQ to;

    private PinsLinkageMQ() {
    }

    public PinsLinkageMQ(String name, PinMQ from, PinMQ to) {
        this.name = name;
        this.from = from;
        this.to = to;
    }

    @Override
    public String getName() {
        return this.name;
    }


    @Override
    public PinMQ getFrom() {
        return this.from;
    }


    @Override
    public PinMQ getTo() {
        return this.to;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this)
            return true;
        if (! (o instanceof PinsLinkageMQ))
            return false;
        return Objects.equals(name, ((PinsLinkageMQ) o).name)
                && Objects.equals(from, ((PinsLinkageMQ) o).from)
                && Objects.equals(to, ((PinsLinkageMQ) o).to);
    }

    @Override
    public int hashCode() {
        throw new AssertionError("method not defined");
    }
}
