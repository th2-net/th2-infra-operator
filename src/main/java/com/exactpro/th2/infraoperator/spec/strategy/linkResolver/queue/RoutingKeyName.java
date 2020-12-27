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

package com.exactpro.th2.infraoperator.spec.strategy.linkResolver.queue;

import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinMQ;

public class RoutingKeyName extends AbstractName {

    public static final RoutingKeyName EMPTY = new RoutingKeyName("","","");

    private static final String EMPTY_ROUTING_KEY = "";
    private static final String ROUTING_KEY_PREFIX = "key";
    private static final String ROUTING_KEY_REGEXP = ROUTING_KEY_PREFIX + "\\[" + NAMESPACE_REGEXP + ":" + BOX_NAME_REGEXP + ":" + PIN_NAME_REGEXP + "\\]";


    public RoutingKeyName(String namespace, PinMQ mqPin) {
        super(namespace, mqPin);
    }


    public RoutingKeyName(String namespace, String boxName, String pinName) {
        super(namespace, boxName, pinName);
    }


    public static RoutingKeyName fromString(String str) {

        if (str == null || str.equals(EMPTY_ROUTING_KEY))
            return RoutingKeyName.EMPTY;

        if (str.matches(ROUTING_KEY_REGEXP)) {
            try {
                String enclosedStr = str.substring(ROUTING_KEY_PREFIX.length() + 1, str.length() - 1);
                String[] tokens = enclosedStr.split(":");
                return new RoutingKeyName(tokens[0], tokens[1], tokens[2]);
            } catch (Exception ignored) {
            }
        }
        return null;
    }


    public static String format(String namespace, String boxName, String pinName) {
        if  (namespace.equals("") && boxName.equals("") && pinName.equals(""))
            return EMPTY_ROUTING_KEY;
        else
            return String.format("%s[%s:%s:%s]", ROUTING_KEY_PREFIX, namespace, boxName, pinName);
    }


    @Override
    public String toString() {
        return RoutingKeyName.format(namespace, boxName, pinName);
    }
}
