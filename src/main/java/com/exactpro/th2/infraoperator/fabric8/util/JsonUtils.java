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

package com.exactpro.th2.infraoperator.fabric8.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

public final class JsonUtils {

    public static final ObjectMapper JSON_READER = new ObjectMapper(new JsonFactory());

    public static final ObjectMapper YAML_READER = new ObjectMapper(new YAMLFactory());


    private JsonUtils() {
        throw new AssertionError();
    }


    public static String writeValueAsString(Object object) throws IOException {
        return JSON_READER.writeValueAsString(object);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> writeValueAsDeepMap(Object object) {
        return (Map<String, Object>) JSON_READER.convertValue(object, Map.class);
    }

    public static Map<String, String> writeValueAsMap(Object object) throws IOException, IllegalAccessException {

        Map<String, String> map = new HashMap<>();

        var fields = getAllFields(object.getClass());

        for (var field : fields) {

            field.setAccessible(true);

            var rawValue = field.get(object);

            var resultValue = writeValueAsString(rawValue);

            resultValue = rawValue.getClass().equals(String.class)
                    ? resultValue.replace("\"", "")
                    : resultValue;

            map.put(field.getName(), resultValue);
        }

        return map;
    }

    public static List<Field> getAllFields(Class<?> type) {
        var fields = new ArrayList<Field>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields;
    }

}
