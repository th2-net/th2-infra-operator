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

package com.exactpro.th2.infraoperator.spec.link;

import com.exactpro.th2.infraoperator.spec.link.relation.BoxesRelation;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.DictionaryBinding;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;

import java.util.ArrayList;
import java.util.List;

@JsonDeserialize(builder = Th2LinkSpec.Builder.class)
public final class Th2LinkSpec implements KubernetesResource {

    private BoxesRelation boxesRelation;

    private List<DictionaryBinding> dictionariesRelation;

    private Th2LinkSpec(BoxesRelation boxesRelation, List<DictionaryBinding> dictionariesRelation) {

        this.boxesRelation = (boxesRelation == null) ? BoxesRelation.newEmptyRelation() : boxesRelation;
        this.dictionariesRelation = (dictionariesRelation == null) ? new ArrayList<>() : dictionariesRelation;
    }

    public BoxesRelation getBoxesRelation() {
        return this.boxesRelation;
    }

    public List<DictionaryBinding> getDictionariesRelation() {
        return this.dictionariesRelation;
    }

    @Override
    public boolean equals(final Object o) {
        throw new AssertionError("method not defined");
    }

    @Override
    public int hashCode() {
        throw new AssertionError("method not defined");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private BoxesRelation boxesRelation;

        private List<DictionaryBinding> dictionariesRelation;

        private Builder() { }

        @JsonProperty("boxes-relation")
        public Builder boxesRelation(BoxesRelation boxesRelation) {
            this.boxesRelation = boxesRelation;
            return this;
        }

        @JsonProperty("dictionaries-relation")
        public Builder dictionariesRelation(List<DictionaryBinding> dictionariesRelation) {
            this.dictionariesRelation = dictionariesRelation;
            return this;
        }

        public Th2LinkSpec build() {
            return new Th2LinkSpec(boxesRelation, dictionariesRelation);
        }
    }
}
