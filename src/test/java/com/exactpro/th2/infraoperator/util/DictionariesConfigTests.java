/*
 * Copyright 2020-2022 Exactpro (Exactpro Systems Limited)
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
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.operator.manager.impl.Th2DictionaryEventHandler.INITIAL_CHECKSUM;
import static com.exactpro.th2.infraoperator.util.HelmReleaseUtils.generateDictionariesConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DictionariesConfigTests {

    @Test
    void testGenerationInSingleLevelConfig() {
        Map<String, Object> customConfig = new HashMap<>();

        customConfig.put("dict1", "${dictionary_link:dictRes1}");
        customConfig.put("dict2", "${dictionary_link:dictRes2Resource}");
        customConfig.put("dict3", "${dictionary_link:dict3}");
        Set<DictionaryEntity> collected = new HashSet<>();
        generateDictionariesConfig(customConfig, collected);
        assertEquals(Set.of(
                "dictRes1-dictionary",
                "dictRes2Resource-dictionary",
                "dict3-dictionary"),
                collected.stream().map(DictionaryEntity::getName).collect(Collectors.toSet()));
        assertEquals("${DICT_RES1}", customConfig.get("dict1"));
        assertEquals("${DICT_RES2_RESOURCE}", customConfig.get("dict2"));
        assertEquals("${DICT3}", customConfig.get("dict3"));
    }

    @Test
    void testGenerationInMultiLevelConfig() {
        Map<String, Object> customConfig = new HashMap<>();
        Map<String, Object> level1 = new HashMap<>();
        Map<String, String> subLevel1 = new HashMap<>();
        Map<String, String> level2 = new HashMap<>();
        customConfig.put("level1", level1);
        customConfig.put("level2", level2);
        level1.put("subLevel1", subLevel1);
        subLevel1.put("dict1", "${dictionary_link:dict1}");
        level2.put("dict2", "${dictionary_link:aDict2B}");
        Set<DictionaryEntity> collected = new HashSet<>();
        generateDictionariesConfig(customConfig, collected);
        assertEquals(Set.of(
                "dict1-dictionary",
                "aDict2B-dictionary"),
                collected.stream().map(DictionaryEntity::getName).collect(Collectors.toSet()));
        assertTrue(collected.stream().allMatch(entity -> entity.getChecksum() != null));
        assertTrue(collected.stream().allMatch(entity -> entity.getChecksum().equals(INITIAL_CHECKSUM)));
        assertEquals("${DICT1}", subLevel1.get("dict1"));
        assertEquals("${A_DICT2_B}", level2.get("dict2"));
    }
}
