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

import java.util.List;

public class Strings {
    private Strings() {
    }

    public static boolean isNullOrEmpty(String s) {
        return (s == null || s.isEmpty());
    }

    public static boolean nonePrefixMatch(String namespace, List<String> prefixes) {
        return (namespace != null
                && prefixes != null
                && prefixes.size() > 0
                && prefixes.stream().noneMatch(namespace::startsWith));
    }

    public static String toUnderScoreUpperCase(String camelCase, int id) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append("_").append(c);
            } else {
                result.append(Character.toUpperCase(c));
            }
        }
        return String.format("%s_%d", result, id);
    }
}
