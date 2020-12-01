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

package com.exactpro.th2.infraoperator.fabric8.model.box.configuration.dictionary;

public class Dictionary {

    private String name;
    private String type;
    private String data;

    Dictionary(String name, String type, String data) {
        this.name = name;
        this.type = type;
        this.data = data;
    }

    public String getName() {
        return this.name;
    }

    public String getType() {
        return this.type;
    }

    public String getData() {
        return this.data;
    }

    public static DictionaryBuilder builder() {
        return new DictionaryBuilder();
    }


    public static class DictionaryBuilder {
        private String name;
        private String type;
        private String data;

        public DictionaryBuilder() {
        }

        public DictionaryBuilder name(String name) {
            this.name = name;
            return this;
        }

        public DictionaryBuilder type(String type) {
            this.type = type;
            return this;
        }

        public DictionaryBuilder data(String data) {
            this.data = data;
            return this;
        }

        public Dictionary build() {
            return new Dictionary(name, type, data);
        }
    }
}
