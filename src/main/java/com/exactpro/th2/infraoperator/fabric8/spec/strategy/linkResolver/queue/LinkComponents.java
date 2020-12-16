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


abstract public class LinkComponents {
    protected final String box;
    protected final String pin;
    protected final String namespace;

    public LinkComponents(BoxMq boxMq, String namespace) {
        this.box = boxMq.getBox();
        this.pin = boxMq.getPin();
        this.namespace = namespace;
    }

    public LinkComponents(String namespace, String box, String pin) {
        this.box = box;
        this.pin = pin;
        this.namespace = namespace;
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
