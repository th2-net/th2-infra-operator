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

import com.exactpro.th2.infraoperator.spec.shared.Identifiable;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Objects;

@JsonDeserialize
public final class DictionaryBinding implements Identifiable {

    private String name;

    private String box;

    private DictionaryDescription dictionary;

    public DictionaryBinding() {}

    public DictionaryDescription getDictionary() {
        return dictionary;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getBox() {
        return box;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DictionaryBinding)) {
            return false;
        }
        DictionaryBinding that = (DictionaryBinding) o;
        return Objects.equals(box, that.box) && Objects.equals(dictionary, that.dictionary);
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
                this.dictionary == null ? "null" : this.dictionary.getName());
    }

    @Override
    public String toString() {
        return String.format("name: %s box: %s dictionary: %s (%s)", name, box,
                dictionary.getName(), dictionary.getType());
    }
}
