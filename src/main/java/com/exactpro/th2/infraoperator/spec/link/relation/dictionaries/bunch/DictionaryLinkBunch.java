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

package com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.bunch;

import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.dictionary.DictionarySpec;
import com.exactpro.th2.infraoperator.spec.shared.Nameable;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.Data;

@Data
@JsonDeserialize
public class DictionaryLinkBunch implements Nameable {

    private String name;

    private String box;

    private DictionarySpec dictionary;


    protected DictionaryLinkBunch() {
    }

    @Builder
    protected DictionaryLinkBunch(String name, String box, DictionarySpec dictionary) {
        this.name = name;
        this.box = box;
        this.dictionary = dictionary;
    }
}
