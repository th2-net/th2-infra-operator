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

package com.exactpro.th2.infraoperator.spec.estore;

import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op;
import com.exactpro.th2.infraoperator.spec.Th2Spec;
import com.exactpro.th2.infraoperator.spec.shared.PinAttribute;
import com.exactpro.th2.infraoperator.spec.shared.pin.MqPin;
import com.exactpro.th2.infraoperator.spec.shared.pin.PinSpec;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Set;

@JsonDeserialize
public class Th2EstoreSpec extends Th2Spec {

    public Th2EstoreSpec() {
        PinSpec autoPins = new PinSpec();
        autoPins.getMq().add(createPin());
        setPins(autoPins);
    }

    private MqPin createPin() {
        return new MqPin(
                pinName(PinAttribute.event),
                Set.of(PinAttribute.subscribe.name(), PinAttribute.event.name())
        );
    }

    private static String pinName(PinAttribute type) {
        return StoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Th2EstoreSpec)) {
            return false;
        }
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        throw new AssertionError("method not defined");
    }
}
