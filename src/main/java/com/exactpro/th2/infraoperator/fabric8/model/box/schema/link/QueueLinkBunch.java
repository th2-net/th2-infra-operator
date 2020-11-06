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

package com.exactpro.th2.infraoperator.fabric8.model.box.schema.link;

import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.boxes.box.impl.BoxMq;
import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.boxes.bunch.impl.MqLinkBunch;
import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.boxes.bunch.BoxLinkBunch;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class QueueLinkBunch implements BoxLinkBunch {

    protected QueueBunch queueBunch;

    protected MqLinkBunch mqLinkBunch;


    public QueueLinkBunch(MqLinkBunch mqLinkBunch) {
        this.mqLinkBunch = mqLinkBunch;
    }

    public QueueLinkBunch(MqLinkBunch mqLinkBunch, QueueBunch queueBunch) {
        this(mqLinkBunch);
        this.queueBunch = queueBunch;
    }


    @Override
    public BoxMq getFrom(){
        return mqLinkBunch.getFrom();
    }

    @Override
    public BoxMq getTo(){
        return mqLinkBunch.getTo();
    }

    @Override
    public String getName() {
        return mqLinkBunch.getName();
    }

}
