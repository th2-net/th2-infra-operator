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

package com.exactpro.th2.infraoperator.spec.mstore;

import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("th2.exactpro.com")
@Version("v1")
@Kind("Th2Mstore")
public class Th2Mstore extends Th2CustomResource {

    private Th2MstoreSpec spec;

    public Th2Mstore() {
    }

    @JsonSetter
    public Th2MstoreSpec getSpec() {
        return this.spec;
    }

    public void setSpec(Th2MstoreSpec spec) {
        this.spec = spec;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Th2Mstore)) {
            return false;
        }
        final Th2Mstore other = (Th2Mstore) o;
        if (!super.equals(o)) {
            return false;
        }
        final Object this$spec = this.getSpec();
        final Object other$spec = other.getSpec();

        return this$spec == null ? other$spec == null : this$spec.equals(other$spec);
    }

    public int hashCode() {
        throw new AssertionError("HashCode is being called");
    }

    public String toString() {
        return "Th2Mstore(super=" + super.toString() + ", spec=" + this.getSpec() + ")";
    }
}
