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

package com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver;

import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.boxes.box.impl.BoxMq;

import static com.exactpro.th2.infraoperator.fabric8.configuration.OperatorConfig.QUEUE_PREFIX;
import static com.exactpro.th2.infraoperator.fabric8.configuration.OperatorConfig.ROUTING_KEY_PREFIX;

public class Queue {
    private final String box;
    private final String pin;
    private final String namespace;

    public Queue(BoxMq boxMq, String namespace) {
        this.box = boxMq.getBox();
        this.pin = boxMq.getPin();
        this.namespace = namespace;
    }

    public Queue(String namespace, String box, String pin) {
        this.box = box;
        this.pin = pin;
        this.namespace = namespace;
    }

    public String toQueueString() {
        return String.format("%s[%s:%s:%s]", QUEUE_PREFIX, namespace, box, pin);
    }

    public String toRoutingKeyString() {
        return String.format("%s[%s:%s:%s]", ROUTING_KEY_PREFIX, namespace, box, pin);
    }

    public static Queue parseQueue(String queueStr) throws IndexOutOfBoundsException {
        String queueBody = queueStr.substring(QUEUE_PREFIX.length() + 1, queueStr.length() - 1);
        String[] queueParts = queueBody.split(":");
        return new Queue(queueParts[0], queueParts[1], queueParts[2]);
    }

    public String getBox() {
        return box;
    }

    public String getPin() {
        return pin;
    }

    public String getNamespace() {
        return namespace;
    }
}
