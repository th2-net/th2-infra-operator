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

package com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue;

import java.util.Objects;

public class QueueName extends AbstractName {

    public static final QueueName EMPTY = new QueueName("", "", "");

    private static final String EMPTY_QUEUE_NAME = "";

    private static final String QUEUE_NAME_PREFIX = "link";

    public static final String QUEUE_NAME_REGEXP =
            QUEUE_NAME_PREFIX + "\\[" + NAMESPACE_REGEXP + ":" + BOX_NAME_REGEXP + ":" + PIN_NAME_REGEXP + "\\]";

    public QueueName(String namespace, String boxName, String pinName) {
        super(namespace, boxName, pinName);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof QueueName)) {
            return false;
        }

        QueueName other = (QueueName) o;
        return Objects.equals(this.namespace, other.namespace) &&
                Objects.equals(this.boxName, other.boxName) &&
                Objects.equals(this.pinName, other.pinName);
    }

    @Override
    public String toString() {
        return QueueName.format(namespace, boxName, pinName);
    }

    public static String format(String namespace, String boxName, String pinName) {
        if (namespace.equals("") && boxName.equals("") && pinName.equals("")) {
            return EMPTY_QUEUE_NAME;
        } else {
            return String.format("%s[%s:%s:%s]", QUEUE_NAME_PREFIX, namespace, boxName, pinName);
        }
    }

    public static QueueName fromString(String str) {

        if (str == null || str.equals(EMPTY_QUEUE_NAME)) {
            return QueueName.EMPTY;
        }
        if (str.matches(QUEUE_NAME_REGEXP)) {
            try {
                String enclosedStr = str.substring(QUEUE_NAME_PREFIX.length() + 1, str.length() - 1);
                String[] tokens = enclosedStr.split(":");
                return new QueueName(tokens[0], tokens[1], tokens[2]);
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
