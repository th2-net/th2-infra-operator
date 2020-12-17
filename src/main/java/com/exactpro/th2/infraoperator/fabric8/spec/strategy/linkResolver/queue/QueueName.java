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


public class QueueName extends AbstractName {

    private static final String QUEUE_NAME_PREFIX = "link";
    private static final String NAMESPACE_REGEXP = "[a-z0-9]([-a-z0-9]*[a-z0-9])?";
    private static final String BOX_NAME_REGEXP = "[a-z0-9]([-a-z0-9]*[a-z0-9])?";
    private static final String PIN_NAME_REGEXP = "[a-z0-9]([-_a-z0-9]*[a-z0-9])?";
    private static final String QUEUE_NAME_REGEXP = QUEUE_NAME_PREFIX + "\\[" + NAMESPACE_REGEXP + ":" + BOX_NAME_REGEXP + ":" + PIN_NAME_REGEXP + "\\]";

    private static final Logger logger = LoggerFactory.getLogger(QueueName.class);

    public QueueName(String namespace, BoxMq boxMq) {
        super(namespace, boxMq);
    }


    public QueueName(String namespace, String boxName, String pinName) {
        super(namespace, boxName, pinName);
    }


    @Override
    public String toString() {
        return QueueName.format(namespace, boxName, pinName);
    }


    public static String format(String namespace, String boxName, String pinName) {
        return String.format("%s[%s:%s:%s]", QUEUE_NAME_PREFIX, namespace, boxName, pinName);
    }


    public static QueueName fromString(String str) {

        if (str.matches(QUEUE_NAME_REGEXP)) {
            try {
                String enclosedStr = str.substring(QUEUE_NAME_PREFIX.length() + 1, str.length() - 1);
                String[] tokens = enclosedStr.split(":");
                return new QueueName(tokens[0], tokens[1], tokens[2]);
            } catch (Exception ignored) {
            }
        }
        logger.warn("Queue name \"{}\" does not match expected pattern \"{}\"", str
                , QueueName.format("namespace", "box", "pin"));
        return null;
    }
}
