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

package com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.queue;

import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.boxes.box.impl.BoxMq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class QueueName extends LinkComponents {

    private static final Logger logger = LoggerFactory.getLogger(QueueName.class);

    private static final String QUEUE_PREFIX = "link";

    public QueueName(BoxMq boxMq, String namespace) {
        super(boxMq, namespace);
    }

    public QueueName(String namespace, String box, String pin) {
        super(namespace, box, pin);
    }

    @Override
    public String toString() {
        return String.format("%s[%s:%s:%s]", QUEUE_PREFIX, namespace, box, pin);
    }

    public static QueueName parseQueue(String queueStr) {
        if (queueStr.startsWith(QUEUE_PREFIX)) {
            try {
                String queueBody = queueStr.substring(QUEUE_PREFIX.length() + 1, queueStr.length() - 1);
                String[] queueParts = queueBody.split(":");
                return new QueueName(queueParts[0], queueParts[1], queueParts[2]);
            } catch (IndexOutOfBoundsException e) {
                logger.warn("Could not parse queue: {}", queueStr, e);
                return null;
            }
        }
        logger.warn("Could not parse queue: {}", queueStr);
        return null;
    }
}
