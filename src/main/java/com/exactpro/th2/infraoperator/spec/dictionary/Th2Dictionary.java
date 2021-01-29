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

package com.exactpro.th2.infraoperator.spec.dictionary;

import com.fasterxml.jackson.annotation.JsonSetter;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

import java.util.Objects;

import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;

@Group("th2.exactpro.com")
@Version("v1")
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

    public Th2DictionarySpec getSpec() {
        return this.spec;
    }

    @JsonSetter
    public void setSpec(Th2DictionarySpec spec) {
        this.spec = spec;
    }

    @Override
    public void setSpec (Object spec) {
        throw new AssertionError("Setting spec with Object argument");
    }

    public String toString() {
        return "Th2Dictionary(super=" + super.toString() + ", spec=" + this.getSpec() + ")";
    }
}

