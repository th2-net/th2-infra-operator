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

package com.exactpro.th2.infraoperator.model.box.schema.link;

import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinMQ;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCoupling;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingMQ;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class QueueLinkBunch implements PinCoupling {

    protected QueueBunch queueBunch;

    protected PinCouplingMQ mqLinkBunch;


    public QueueLinkBunch(PinCouplingMQ mqLinkBunch) {
        this.mqLinkBunch = mqLinkBunch;
    }

    public QueueLinkBunch(PinCouplingMQ mqLinkBunch, QueueBunch queueBunch) {
        this(mqLinkBunch);
        this.queueBunch = queueBunch;
    }


    @Override
    public PinMQ getFrom() {
        return mqLinkBunch.getFrom();
    }

    @Override
    public PinMQ getTo() {
        return mqLinkBunch.getTo();
    }

    public String getName() {
        return mqLinkBunch.getName();
    }

    @Override
    public String getId() {
        return String.format("%s[%s:%s](%s)",
                queueBunch.getExchange(),
                queueBunch.getQueue(),
                queueBunch.getRoutingKey(),
                mqLinkBunch.getId()
        );
    }
}
