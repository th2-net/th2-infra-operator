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

public class MultiDictionaryEntity {

    private final String name;

    private String checksum;

    private final String alias;

    public MultiDictionaryEntity(String name, String checksum, String alias) {
        this.name = name;
        this.checksum = checksum;
        this.alias = alias;
    }

    public String getName() {
        return this.name;
    }

    public String getChecksum() {
        return this.checksum;
    }

    public String getAlias() {
        return alias;
    }

    public void updateChecksum(String checksum) {
        this.checksum = checksum;
    }
}
