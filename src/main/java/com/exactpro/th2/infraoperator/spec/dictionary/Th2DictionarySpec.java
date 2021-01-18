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
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonDeserialize
public class Th2DictionarySpec implements KubernetesResource {

    private String data;

    private boolean compressed;

    protected Th2DictionarySpec() {
    }

    protected Th2DictionarySpec(String data) {
        this.data = data;
    }

    protected Th2DictionarySpec(String data, boolean compressed) {
        this.data = data;
        this.compressed = compressed;
    }
}
