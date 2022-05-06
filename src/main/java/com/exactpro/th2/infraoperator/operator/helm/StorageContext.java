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

package com.exactpro.th2.infraoperator.operator.helm;

import java.util.Set;

public abstract class StorageContext {

    private final String linkResourceName;

    private final String linkNameSuffix;

    private final String boxAlias;

    private final String pinName;

    protected StorageContext(String linkResourceName, String linkNameSuffix,
                             String boxAlias, String pinName) {
        this.linkResourceName = linkResourceName;
        this.linkNameSuffix = linkNameSuffix;
        this.boxAlias = boxAlias;
        this.pinName = pinName;
    }

    public abstract boolean checkAttributes(Set<String> attributes, String pinAnnotation);

    public String getLinkResourceName() {
        return this.linkResourceName;
    }

    public String getLinkNameSuffix() {
        return this.linkNameSuffix;
    }

    public String getBoxAlias() {
        return this.boxAlias;
    }

    public String getPinName() {
        return this.pinName;
    }

}
