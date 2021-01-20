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
import com.exactpro.th2.infraoperator.spec.shared.PinSpec;
import com.exactpro.th2.infraoperator.spec.shared.SchemaConnectionType;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@JsonDeserialize
public class Th2EstoreSpec extends Th2Spec {

    public Th2EstoreSpec() {

        List<PinSpec> autoPins = new ArrayList<>();
        autoPins.add(createPin(PinAttribute.event));
        setPins(autoPins);
    }


    private PinSpec createPin(PinAttribute type) {
        PinSpec pin = new PinSpec();
        pin.setName(pinName(type));
        pin.setConnectionType(SchemaConnectionType.mq);
        pin.setAttributes(Set.of(PinAttribute.subscribe.name(), type.name()));
        return pin;
    }


    private static String pinName(PinAttribute type) {
        return StoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS;
    }


    @Override
    public boolean equals(final Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Th2EstoreSpec))
            return false;

        return super.equals(o);
    }


    @Override
    public int hashCode() {
        throw new AssertionError("method not defined");
    }
}
