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

package com.exactpro.th2.infraoperator.spec.dictionary;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;

@JsonDeserialize
public class Th2DictionarySpec implements KubernetesResource {

    private String data;

    private boolean compressed;

    public Th2DictionarySpec() {
    }

    public Th2DictionarySpec(String data) {
        this.data = data;
    }

    public Th2DictionarySpec(String data, boolean compressed) {
        this.data = data;
        this.compressed = compressed;
    }

    public String getData() {
        return this.data;
    }

    public boolean isCompressed() {
        return this.compressed;
    }

    public void setData(String data) {
        this.data = data;
    }

    public void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }

    public boolean equals(final Object o) {
        throw new AssertionError("method not defined");
    }

    public int hashCode() {
        throw new AssertionError("method not defined");
    }

    public String toString() {
        return "Th2DictionarySpec(data=" + this.getData() + ", compressed=" + this.isCompressed() + ")";
    }
}
