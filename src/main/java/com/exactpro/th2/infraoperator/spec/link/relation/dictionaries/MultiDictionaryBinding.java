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

package com.exactpro.th2.infraoperator.spec.link.relation.dictionaries;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonDeserialize(builder = MultiDictionaryBinding.Builder.class)
public final class MultiDictionaryBinding extends AbstractDictionaryBinding {

    private final List<MultiDictionaryDescription> dictionaries;

    private MultiDictionaryBinding(String name, String box, List<MultiDictionaryDescription> dictionaries) {
        super(name, box);
        this.dictionaries = dictionaries != null ? dictionaries : new ArrayList<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<MultiDictionaryDescription> getDictionaries() {
        return dictionaries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MultiDictionaryBinding)) {
            return false;
        }
        MultiDictionaryBinding that = (MultiDictionaryBinding) o;
        return Objects.equals(box, that.box) && Objects.equals(dictionaries, that.dictionaries);
    }

    @Override
    public int hashCode() {
        throw new AssertionError("method not defined");
    }

    @Override
    public String getId() {
        return String.format("%s[%s:%s]",
                this.getClass().getSimpleName(),
                this.box,
                this.dictionaries == null ? "null" : asString());
    }

    @Override
    public String toString() {
        return String.format("name: %s box: %s dictionaries: %s", name, box, asString());
    }

    private String asString() {
        StringBuilder dictionaryNames = new StringBuilder();
        for (MultiDictionaryDescription dictionaryDescription : dictionaries) {
            dictionaryNames.append(dictionaryDescription.getName())
                    .append("-")
                    .append(dictionaryDescription.getAlias());
        }
        return dictionaryNames.toString();
    }

    public static class Builder {

        private String name;

        private String box;

        private List<MultiDictionaryDescription> dictionaries;

        Builder() {
        }

        @JsonProperty("name")
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @JsonProperty("box")
        public Builder box(String box) {
            this.box = box;
            return this;
        }

        @JsonProperty("dictionaries")
        public Builder dictionaries(List<MultiDictionaryDescription> dictionaries) {
            this.dictionaries = dictionaries;
            return this;
        }

        private MultiDictionaryBinding build() {
            return new MultiDictionaryBinding(name, box, dictionaries);
        }
    }
}
