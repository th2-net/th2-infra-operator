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

package com.exactpro.th2.infraoperator.spec.shared.status;

import java.util.Objects;

public class Condition {

    private String lastTransitionTime;

    private String lastUpdateTime;

    private String message;

    private String reason;

    private String status;

    private String type;

    protected Condition() { }

    protected Condition(String lastTransitionTime, String lastUpdateTime, String message, String reason,
                        String status, String type) {
        this.lastTransitionTime = lastTransitionTime;
        this.lastUpdateTime = lastUpdateTime;
        this.message = message;
        this.reason = reason;
        this.status = status;
        this.type = type;
    }

    public static ConditionBuilder builder() {
        return new ConditionBuilder();
    }

    public String getLastTransitionTime() {
        return this.lastTransitionTime;
    }

    public String getLastUpdateTime() {
        return this.lastUpdateTime;
    }

    public String getMessage() {
        return this.message;
    }

    public String getReason() {
        return this.reason;
    }

    public String getStatus() {
        return this.status;
    }

    public String getType() {
        return this.type;
    }

    public void setLastTransitionTime(String lastTransitionTime) {
        this.lastTransitionTime = lastTransitionTime;
    }

    public void setLastUpdateTime(String lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Condition)) {
            return false;
        }
        Condition condition = (Condition) o;
        return Objects.equals(lastTransitionTime, condition.lastTransitionTime) &&
                Objects.equals(lastUpdateTime, condition.lastUpdateTime) &&
                Objects.equals(message, condition.message) &&
                Objects.equals(reason, condition.reason) &&
                Objects.equals(status, condition.status) &&
                Objects.equals(type, condition.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lastTransitionTime, lastUpdateTime, message, reason, status, type);
    }

    @Override
    public String toString() {
        return "Condition{" +
                "lastTransitionTime='" + lastTransitionTime + '\'' +
                ", lastUpdateTime='" + lastUpdateTime + '\'' +
                ", message='" + message + '\'' +
                ", reason='" + reason + '\'' +
                ", status='" + status + '\'' +
                ", type='" + type + '\'' +
                '}';
    }

    public enum Type {
        DEPLOYED("Deployed");

        private final String name;

        Type(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum Status {
        TRUE("True"),
        FALSE("False"),
        UNKNOWN("Unknown");

        private final String name;

        Status(String name) {
            this.name = name;
        }

        public static Status valueOf(Boolean status) {
            return status == null ? Status.UNKNOWN : (status ? Status.TRUE : Status.FALSE);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class ConditionBuilder {
        private String lastTransitionTime;

        private String lastUpdateTime;

        private String message;

        private String reason;

        private String status;

        private String type;

        ConditionBuilder() {
        }

        public ConditionBuilder lastTransitionTime(String lastTransitionTime) {
            this.lastTransitionTime = lastTransitionTime;
            return this;
        }

        public ConditionBuilder lastUpdateTime(String lastUpdateTime) {
            this.lastUpdateTime = lastUpdateTime;
            return this;
        }

        public ConditionBuilder message(String message) {
            this.message = message;
            return this;
        }

        public ConditionBuilder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public ConditionBuilder status(String status) {
            this.status = status;
            return this;
        }

        public ConditionBuilder type(String type) {
            this.type = type;
            return this;
        }

        public Condition build() {
            return new Condition(lastTransitionTime, lastUpdateTime, message, reason, status, type);
        }
    }
}
