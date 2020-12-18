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

package com.exactpro.th2.infraoperator.spec.shared.status;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Condition {

    private String lastTransitionTime;
    private String lastUpdateTime;
    private String message;
    private String reason;
    private String status;
    private String type;


    protected Condition() {
    }

    protected Condition(String lastTransitionTime, String lastUpdateTime, String message, String reason, String status, String type) {
        this.lastTransitionTime = lastTransitionTime;
        this.lastUpdateTime = lastUpdateTime;
        this.message = message;
        this.reason = reason;
        this.status = status;
        this.type = type;
    }


    public enum Type {
        DEPLOYED("Deployed"),
        ENQUEUED("Enqueued");

        private String name;
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

        private String name;
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

}
