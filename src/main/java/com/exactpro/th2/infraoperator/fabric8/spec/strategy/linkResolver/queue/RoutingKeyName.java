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

public class RoutingKeyName extends LinkComponents {

    public static final String ROUTING_KEY_PREFIX = "key";

    public RoutingKeyName(BoxMq boxMq, String namespace) {
        super(boxMq, namespace);
    }

    public RoutingKeyName(String namespace, String box, String pin) {
        super(namespace, box, pin);
    }

    @Override
    public String toString() {
        return String.format("%s[%s:%s:%s]", ROUTING_KEY_PREFIX, namespace, box, pin);
    }
}
