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

package com.exactpro.th2.infraoperator.spec.link.relation.dictionaries;

import com.exactpro.th2.infraoperator.spec.shared.Nameable;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = DictionaryBinding.Builder.class)
public final class DictionaryBinding implements Nameable {

    private final String name;
    private final String box;
    private final DictionaryDescription dictionary;


    private DictionaryBinding(String name, String box, DictionaryDescription dictionary) {
        this.name = name;
        this.box = box;
        this.dictionary = dictionary;
    }


    public static Builder builder() {
        return new Builder();
    }


    public String getName() {
        return this.name;
    }


    public String getBox() {
        return this.box;
    }


    public DictionaryDescription getDictionary() {
        return this.dictionary;
    }


    @Override
    public boolean equals(final Object o) {
        throw new AssertionError("method not defined");
    }


    @Override
    public int hashCode() {
        throw new AssertionError("method not defined");
    }


    public static class Builder {
        private String name;
        private String box;
        private DictionaryDescription dictionary;

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


        @JsonProperty("dictionary")
        public Builder dictionary(DictionaryDescription dictionary) {
            this.dictionary = dictionary;
            return this;
        }

        private DictionaryBinding build() {
            return new DictionaryBinding(name, box, dictionary);
        }
    }
}
