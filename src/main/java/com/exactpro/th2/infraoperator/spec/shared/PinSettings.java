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

package com.exactpro.th2.infraoperator.spec.shared;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PinSettings {

    private boolean storageOnDemand;

    private int queueLength;

    private String overloadStrategy;

    public boolean getStorageOnDemand() {
        return this.storageOnDemand;
    }

    public int getQueueLength() {
        return this.queueLength;
    }

    public String getOverloadStrategy() {
        return this.overloadStrategy;
    }

    public void setStorageOnDemand(boolean storageOnDemand) {
        this.storageOnDemand = storageOnDemand;
    }

    public void setQueueLength(int queueLength) {
        this.queueLength = queueLength;
    }

    public void setOverloadStrategy(String overloadStrategy) {
        this.overloadStrategy = overloadStrategy;
    }

    @Override
    public boolean equals(Object o) {
        throw new AssertionError("method not defined");
    }

    @Override
    public int hashCode() {
        throw new AssertionError("method not defined");
    }

    @Override
    public String toString() {
        return "PinSettings{" +
                "storageOnDemand='" + storageOnDemand + '\'' +
                ", queueLength='" + queueLength + '\'' +
                ", overloadStrategy='" + overloadStrategy + '\'' +
                '}';
    }
}
