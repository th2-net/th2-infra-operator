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

package com.exactpro.th2.infraoperator.spec;

import com.exactpro.th2.infraoperator.spec.shared.status.StatusSpec;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;

public abstract class Th2CustomResource extends CustomResource implements Namespaced {

    protected StatusSpec status = new StatusSpec();

    public Th2CustomResource() { }

    public abstract Th2Spec getSpec();

    @Override
    public void setSpec(Object spec) {
        throw new AssertionError("Setting spec with Object argument");
    }

    public StatusSpec getStatus() {
        return this.status;
    }

    public void setStatus(StatusSpec status) {
        this.status = status;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Th2CustomResource)) {
            return false;
        }
        final Th2CustomResource other = (Th2CustomResource) o;
        if (!super.equals(o)) {
            return false;
        }
        final Object this$status = this.getStatus();
        final Object other$status = other.getStatus();

        return this$status == null ? other$status == null : this$status.equals(other$status);
    }

    public int hashCode() {
        throw new AssertionError("HashCode is being called");
    }

    public String toString() {
        return "Th2CustomResource(super=" + super.toString() + ", status=" + this.getStatus() + ")";
    }
}
