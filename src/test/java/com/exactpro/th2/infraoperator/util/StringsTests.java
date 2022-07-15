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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StringsTests {

    @Test
    void isNullOrEmptyTest() {
        String testStr = null;
        assertTrue(Strings.isNullOrEmpty(testStr));
        testStr = "";
        assertTrue(Strings.isNullOrEmpty(testStr));
        testStr = "notNullOrEmpty";
        assertFalse(Strings.isNullOrEmpty(testStr));
    }

    @Test
    void nonePrefixMatchTest() {
        List<String> prefixes = List.of("a", "b", "c", "D");
        String namespace = "dev-someone";
        assertTrue(Strings.nonePrefixMatch(namespace, prefixes));
        prefixes = List.of("dev", "not-dev");
        assertFalse(Strings.nonePrefixMatch(namespace, prefixes));
        assertFalse(Strings.nonePrefixMatch(null, null));
    }

    @Test
    void toUnderScoreUpperCaseTest() {
        int id = 0;
        String camelCase = "camelCaseVeryVeryLongExample";
        String expected = String.format("%s_%d", "CAMEL_CASE_VERY_VERY_LONG_EXAMPLE", id);
        assertEquals(expected, Strings.toUnderScoreUpperCaseWithId(camelCase, id));
        camelCase = "simplerCamel";
        id = Integer.MAX_VALUE;
        expected = String.format("%s_%d", "SIMPLER_CAMEL", id);
        assertEquals(expected, Strings.toUnderScoreUpperCaseWithId(camelCase, id));
    }
}
