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

package com.exactpro.th2.infraoperator.model.box.configuration.dictionary;

public class DictionaryEntity {

    private final String name;

    private final String type;

    private final String checksum;

    DictionaryEntity(String name, String type, String checksum) {
        this.name = name;
        this.type = type;
        this.checksum = checksum;
    }

    public String getName() {
        return this.name;
    }

    public String getType() {
        return this.type;
    }

    public String getChecksum() {
        return this.checksum;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;

        private String type;

        private String checksum;

        public Builder() {
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setType(String type) {
            this.type = type;
            return this;
        }

        public Builder setChecksum(String checksum) {
            this.checksum = checksum;
            return this;
        }

        public DictionaryEntity build() {
            return new DictionaryEntity(name, type, checksum);
        }
    }
}
