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

import com.exactpro.th2.infraoperator.model.box.dictionary.DictionaryEntity;
import org.apache.commons.text.lookup.StringLookup;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.exactpro.th2.infraoperator.operator.manager.impl.Th2DictionaryEventHandler.DICTIONARY_SUFFIX;
import static com.exactpro.th2.infraoperator.operator.manager.impl.Th2DictionaryEventHandler.INITIAL_CHECKSUM;

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

    public static String toUnderScoreUpperCaseWithId(String camelCase, int id) {
        return String.format("%s_%d", toUnderScoreUpperCase(camelCase), id);
    }

    public static String toUnderScoreUpperCase(String camelCase) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append("_").append(c);
            } else {
                result.append(Character.toUpperCase(c));
            }
        }
        return result.toString();
    }

    public static final class CustomLookupForSecrets implements StringLookup {
        private final Map<String, String> collector;

        private int id = 0;

        public CustomLookupForSecrets(Map<String, String> collector) {
            this.collector = collector;
        }

        @Override
        public String lookup(String key) {
            String envVarWithId = Strings.toUnderScoreUpperCaseWithId(key, id);
            id++;
            collector.put(envVarWithId, key);
            return String.format("${%s}", envVarWithId);
        }
    }

    public static final class CustomLookupForDictionaries implements StringLookup {
        private final Set<DictionaryEntity> collector;

        public CustomLookupForDictionaries(Set<DictionaryEntity> collector) {
            this.collector = collector;
        }

        @Override
        public String lookup(String key) {
            String envVar = Strings.toUnderScoreUpperCase(key);
            collector.add(new DictionaryEntity(key + DICTIONARY_SUFFIX, INITIAL_CHECKSUM));
            return String.format("${%s}", envVar);
        }
    }
}
