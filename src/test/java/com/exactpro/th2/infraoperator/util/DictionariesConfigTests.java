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
import com.exactpro.th2.infraoperator.spec.Th2Spec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
        Th2Spec spec = new Th2Spec();
        Map<String, Object> customConfig = new HashMap<>();
        spec.setCustomConfig(customConfig);
        customConfig.put("dict1", "${dictionary_link:dictRes1}");
        customConfig.put("dict2", "${dictionary_link:dictRes2Resource}");
        customConfig.put("dict3", "${dictionary_link:dict3}");
        Set<DictionaryEntity> collected = new HashSet<>();
        generateDictionariesConfig(spec, collected);
        assertEquals(Set.of(
                        "dictRes1-dictionary",
                        "dictRes2Resource-dictionary",
                        "dict3-dictionary"),
                collected.stream().map(DictionaryEntity::getName).collect(Collectors.toSet()));
        customConfig = spec.getCustomConfig();
        assertEquals("dictRes1-dictionary", customConfig.get("dict1"));
        assertEquals("dictRes2Resource-dictionary", customConfig.get("dict2"));
        assertEquals("dict3-dictionary", customConfig.get("dict3"));
    }

    @Test
    void testGenerationInMultiLevelConfig() {
        Th2Spec spec = new Th2Spec();
        Map<String, Object> customConfig = new HashMap<>();
        Map<String, Object> level1 = new HashMap<>();
        Map<String, String> subLevel1 = new HashMap<>();
        Map<String, String> level2 = new HashMap<>();
        List<String> level3 = new ArrayList<>();
        customConfig.put("level1", level1);
        customConfig.put("level2", level2);
        customConfig.put("level3", level3);
        level1.put("subLevel1", subLevel1);
        subLevel1.put("dict1", "${dictionary_link:dict1}");
        level2.put("dict2", "${dictionary_link:aDict2B}");
        level3.add("${dictionary_link:dict1}");
        level3.add("${dictionary_link:dict2}");
        Set<DictionaryEntity> collected = new HashSet<>();
        spec.setCustomConfig(customConfig);
        generateDictionariesConfig(spec, collected);
        assertEquals(Set.of(
                        "dict1-dictionary",
                        "aDict2B-dictionary",
                        "dict2-dictionary"),
                collected.stream().map(DictionaryEntity::getName).collect(Collectors.toSet()));
        assertTrue(collected.stream().allMatch(entity -> entity.getChecksum() != null));
        assertTrue(collected.stream().allMatch(entity -> entity.getChecksum().equals(INITIAL_CHECKSUM)));
        level1 = (Map<String, Object>) spec.getCustomConfig().get("level1");
        subLevel1 = (Map<String, String>) level1.get("subLevel1");
        level2 = (Map<String, String>) spec.getCustomConfig().get("level2");
        level3 = (List<String>) spec.getCustomConfig().get("level3");
        assertEquals("dict1-dictionary", subLevel1.get("dict1"));
        assertEquals("aDict2B-dictionary", level2.get("dict2"));
        assertEquals(List.of(
                        "dict1-dictionary",
                        "dict2-dictionary"),
                level3);
    }
}
