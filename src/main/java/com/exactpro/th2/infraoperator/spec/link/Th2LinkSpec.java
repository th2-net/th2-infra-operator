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
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.MultiDictionaryBinding;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;

import java.util.ArrayList;
import java.util.List;

@JsonDeserialize
public final class Th2LinkSpec implements KubernetesResource {

    private final BoxesRelation boxesRelation;

    private final List<DictionaryBinding> dictionariesRelation;

    private final List<MultiDictionaryBinding> multiDictionariesRelation;

    public Th2LinkSpec() {
        this.boxesRelation = BoxesRelation.newEmptyRelation();
        this.dictionariesRelation = new ArrayList<>();
        this.multiDictionariesRelation = new ArrayList<>();
    }

    public BoxesRelation getBoxesRelation() {
        return this.boxesRelation;
    }

    public List<DictionaryBinding> getDictionariesRelation() {
        return this.dictionariesRelation;
    }

    public List<MultiDictionaryBinding> getMultiDictionariesRelation() {
        return multiDictionariesRelation;
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
