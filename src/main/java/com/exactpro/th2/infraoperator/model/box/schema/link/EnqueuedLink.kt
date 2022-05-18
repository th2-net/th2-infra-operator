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

package com.exactpro.th2.infraoperator.model.box.schema.link

import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCoupling
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingMQ
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinMQ

data class EnqueuedLink(
    val pinCoupling: PinCouplingMQ,
    val queueDescription: QueueDescription
) : PinCoupling {

    override fun getFrom(): PinMQ {
        return pinCoupling.from
    }

    override fun getTo(): PinMQ {
        return pinCoupling.to
    }

    override fun getName(): String {
        return pinCoupling.name
    }

    override fun getId(): String {
        return pinCoupling.id
    }
}
