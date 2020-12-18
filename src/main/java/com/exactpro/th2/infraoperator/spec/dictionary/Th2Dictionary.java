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

package com.exactpro.th2.infraoperator.spec.dictionary;

import io.fabric8.kubernetes.client.CustomResource;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;

import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;

@Getter
@Setter
@ToString(callSuper = true)
public class Th2Dictionary extends CustomResource {

    private Th2DictionarySpec spec;


    private Th2Dictionary() {
    }


    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Th2Dictionary)) return false;
        Th2Dictionary th2Dictionary = (Th2Dictionary) object;
        return extractName(this).equals(extractName(th2Dictionary))
                && extractNamespace(this).equals(extractNamespace(th2Dictionary));
    }

    @Override
    public int hashCode() {
        return Objects.hash(extractName(this), extractNamespace(this));
    }

}

