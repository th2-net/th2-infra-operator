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

import java.util.Objects;

@JsonDeserialize
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PinSettings {

    private String storageOnDemand;

    private String queueLength;

    private String overloadStrategy;

    public String getStorageOnDemand() {
        return this.storageOnDemand;
    }

    public String getQueueLength() {
        return this.queueLength;
    }

    public String getOverloadStrategy() {
        return this.overloadStrategy;
    }

    public void setStorageOnDemand(String storageOnDemand) {
        this.storageOnDemand = storageOnDemand;
    }

    public void setQueueLength(String queueLength) {
        this.queueLength = queueLength;
    }

    public void setOverloadStrategy(String overloadStrategy) {
        this.overloadStrategy = overloadStrategy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PinSettings)) {
            return false;
        }
        PinSettings that = (PinSettings) o;
        return Objects.equals(storageOnDemand, that.storageOnDemand) &&
                Objects.equals(queueLength, that.queueLength) &&
                Objects.equals(overloadStrategy, that.overloadStrategy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storageOnDemand, queueLength, overloadStrategy);
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
