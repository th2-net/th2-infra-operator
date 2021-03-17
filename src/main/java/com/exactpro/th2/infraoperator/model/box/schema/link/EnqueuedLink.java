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

package com.exactpro.th2.infraoperator.model.box.schema.link;

import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCoupling;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingMQ;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinMQ;

import java.util.Objects;

public final class EnqueuedLink implements PinCoupling {

    private QueueDescription queueDescription;

    private PinCouplingMQ pinCoupling;

    public EnqueuedLink(PinCouplingMQ pinCoupling, QueueDescription queueDescription) {
        this.pinCoupling = pinCoupling;
        this.queueDescription = queueDescription;
    }

    @Override
    public PinMQ getFrom() {
        return pinCoupling.getFrom();
    }

    @Override
    public PinMQ getTo() {
        return pinCoupling.getTo();
    }

    public String getName() {
        return pinCoupling.getName();
    }

    @Override
    public String getId() {
        return pinCoupling.getId();
    }

    public QueueDescription getQueueDescription() {
        return this.queueDescription;
    }

    public PinCouplingMQ getPinCoupling() {
        return this.pinCoupling;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EnqueuedLink)) {
            return false;
        }
        return Objects.equals(this.queueDescription, ((EnqueuedLink) o).queueDescription) &&
                Objects.equals(this.pinCoupling, ((EnqueuedLink) o).pinCoupling);
    }

    @Override
    public int hashCode() {
        throw new AssertionError("method not implemented");
    }

    @Override
    public String toString() {
        return String.format("%s[%s:%s]", this.getClass().getName(),
                queueDescription.toString(), pinCoupling.toString());
    }
}
