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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Objects;

@JsonDeserialize
public final class DictionarySpec {

    public static final String DEFAULT_DICTIONARY_TYPE = "MAIN";

    private String name;
    private String type;


    public DictionarySpec(@JsonProperty("name") String name, @JsonProperty("type") String type) {
        this.name = name;
        this.type = type == null ? DEFAULT_DICTIONARY_TYPE : type;
    }


    public String getName() {
        return this.name;
    }


    public String getType() {
        return this.type;
    }


    public boolean equals(final Object o) {
        if (o == this)
            return true;
        if (!(o instanceof DictionarySpec))
            return false;
        return Objects.equals(name, ((DictionarySpec) o).name)
                && Objects.equals(type, ((DictionarySpec) o).type);
    }

    @Override
    public int hashCode() {
        throw new AssertionError("method not defined");
    }
}
