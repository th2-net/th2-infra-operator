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

import com.fasterxml.jackson.annotation.JsonSetter;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;

import java.util.Objects;

import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;


@Group("th2.exactpro.com")
@Version("v1")
@Kind("Th2Link")
public class Th2Link extends CustomResource {

    private Th2LinkSpec spec;

    /*
        It's not recommended to instantiate objects via
        constructor, it's made public because of Fabric8,
        You should use `newInstance` method instead!
     */
    @Deprecated
    public Th2Link() {
    }


    public static Th2Link newInstance() {

        Th2Link th2Link = new Th2Link();
        th2Link.setSpec(Th2LinkSpec.builder().build());
        return th2Link;
    }


    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (!(object instanceof Th2Link))
            return false;
        Th2Link th2Link = (Th2Link) object;
        return extractName(this).equals(extractName(th2Link))
                && extractNamespace(this).equals(extractNamespace(th2Link));
    }

    @Override
    public int hashCode() {
        return Objects.hash(extractName(this), extractNamespace(this));
    }

    @Override
    public void setSpec (Object spec) {
        throw new AssertionError("Setting spec with Object argument");
    }

    @JsonSetter
    public void setSpec(Th2LinkSpec spec) {
        this.spec = spec;
    }

    public Th2LinkSpec getSpec() {
        return spec;
    }

    public String toString() {
        return "Th2Link(super=" + super.toString() + ", spec=" + this.getSpec() + ")";
    }
}

