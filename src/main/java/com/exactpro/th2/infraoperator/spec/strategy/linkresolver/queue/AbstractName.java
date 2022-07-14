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

abstract class AbstractName {
    protected static final String NAMESPACE_REGEXP = "[a-z0-9]([-a-z0-9]*[a-z0-9])?";

    protected static final String BOX_NAME_REGEXP = "[a-z0-9]([-a-z0-9]*[a-z0-9])?";

    protected static final String PIN_NAME_REGEXP = "[a-z0-9]([-_a-z0-9]*[a-z0-9])?";

    protected final String boxName;

    protected final String pinName;

    protected final String namespace;

    protected AbstractName(String namespace, String boxName, String pinName) {
        this.boxName = boxName;
        this.pinName = pinName;
        this.namespace = namespace;
    }

    public String getBoxName() {
        return boxName;
    }

    public String getPinName() {
        return pinName;
    }

    public String getNamespace() {
        return namespace;
    }
}
