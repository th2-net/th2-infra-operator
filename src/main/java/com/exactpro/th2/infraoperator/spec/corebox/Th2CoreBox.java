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

package com.exactpro.th2.infraoperator.spec.corebox;

import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.Th2Spec;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;

import java.util.Objects;

@Group("th2.exactpro.com")
@Version("v1")
@Kind("Th2CoreBox")
public class Th2CoreBox extends Th2CustomResource {

    private Th2Spec spec;

    public Th2CoreBox() { }

    public Th2Spec getSpec() {
        return this.spec;
    }

    @JsonSetter
    public void setSpec(Th2Spec spec) {
        this.spec = spec;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Th2CoreBox)) {
            return false;
        }
        final Th2CoreBox other = (Th2CoreBox) o;
        if (!super.equals(o)) {
            return false;
        }
        final Object thisSpec = this.getSpec();
        final Object otherSpec = other.getSpec();

        return Objects.equals(thisSpec, otherSpec);
    }

    public int hashCode() {
        throw new AssertionError("HashCode is being called");
    }

    public String toString() {
        return "Th2CoreBox(super=" + super.toString() + ", spec=" + this.getSpec() + ")";
    }
}
