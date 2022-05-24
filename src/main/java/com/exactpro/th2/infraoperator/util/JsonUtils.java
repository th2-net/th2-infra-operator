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

package com.exactpro.th2.infraoperator.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.module.kotlin.KotlinModule;

import java.util.*;

public class JsonUtils {

    public static final ObjectMapper JSON_READER = new ObjectMapper(new JsonFactory())
            .registerModule(new KotlinModule.Builder().build())
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);

    public static final ObjectMapper YAML_READER = new ObjectMapper(new YAMLFactory())
            .registerModule(new KotlinModule.Builder().build());

    private JsonUtils() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> writeValueAsDeepMap(Object object) {
        return (Map<String, Object>) JSON_READER.convertValue(object, Map.class);
    }

}
