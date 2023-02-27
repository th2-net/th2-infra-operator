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

import com.exactpro.th2.infraoperator.spec.Th2Spec;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.exactpro.th2.infraoperator.util.HelmReleaseUtils.generateSecretsConfig;
import static org.junit.jupiter.api.Assertions.*;

class SecretConfigTests {

    @Test
    void singleLevelConfigTest() {
        Th2Spec spec = new Th2Spec();
        Map<String, Object> customConfig = new HashMap<>();
        customConfig.put("pass1", "${secret_value:myFixPassword}");
        customConfig.put("pass2", "${secret_value:myOtherPassword}");
        customConfig.put("user1", "${secret_value:myFixUser}");
        customConfig.put("file1", "${secret_path:mySecretFile}");
        customConfig.put("file2", "${secret_path:mySecRetFile}");

        Map<String, String> secretValuesConfig = new HashMap<>();
        Map<String, String> secretPathsConfig = new HashMap<>();
        spec.setCustomConfig(customConfig);
        generateSecretsConfig(spec, secretValuesConfig, secretPathsConfig);

        customConfig = spec.getCustomConfig();
        // check custom config
        assertEquals("${MY_FIX_PASSWORD_1}", customConfig.get("pass1"));
        assertEquals("${MY_OTHER_PASSWORD_2}", customConfig.get("pass2"));
        assertEquals("${MY_FIX_USER_0}", customConfig.get("user1"));
        assertEquals("${MY_SECRET_FILE_1}", customConfig.get("file1"));
        assertEquals("${MY_SEC_RET_FILE_0}", customConfig.get("file2"));

        //check secret values config
        assertEquals(3, secretValuesConfig.size());
        assertEquals("myFixPassword", secretValuesConfig.get("MY_FIX_PASSWORD_1"));
        assertEquals("myOtherPassword", secretValuesConfig.get("MY_OTHER_PASSWORD_2"));
        assertEquals("myFixUser", secretValuesConfig.get("MY_FIX_USER_0"));

        //check secret paths config
        assertEquals(2, secretPathsConfig.size());
        assertEquals("mySecretFile", secretPathsConfig.get("MY_SECRET_FILE_1"));
        assertEquals("mySecRetFile", secretPathsConfig.get("MY_SEC_RET_FILE_0"));
    }

    @Test
    void emptySecretValuesConfigTest() {
        Th2Spec spec = new Th2Spec();
        Map<String, Object> customConfig = new HashMap<>();
        customConfig.put("noValue1", "${secret_value:}");
        customConfig.put("noValue2", "${secret_value}");
        Map<String, String> secretValuesConfig = new HashMap<>();
        Map<String, String> secretPathsConfig = new HashMap<>();
        spec.setCustomConfig(customConfig);
        assertDoesNotThrow(() -> generateSecretsConfig(spec, secretValuesConfig, secretPathsConfig));
    }

    @Test
    void suffixedTextTest() {
        Th2Spec spec = new Th2Spec();
        Map<String, Object> customConfig = new HashMap<>();
        customConfig.put("pass1", "${secret_value:myFixPassword}someText");
        customConfig.put("user1", "${secret_value:myFixUser}someOtherText");

        Map<String, String> secretValuesConfig = new HashMap<>();
        Map<String, String> secretPathsConfig = new HashMap<>();
        spec.setCustomConfig(customConfig);
        generateSecretsConfig(spec, secretValuesConfig, secretPathsConfig);

        customConfig = spec.getCustomConfig();
        // check custom config
        assertEquals("${MY_FIX_PASSWORD_1}someText", customConfig.get("pass1"));
        assertEquals("${MY_FIX_USER_0}someOtherText", customConfig.get("user1"));

        //check secret values config
        assertEquals(2, secretValuesConfig.size());
        assertEquals("myFixPassword", secretValuesConfig.get("MY_FIX_PASSWORD_1"));
        assertEquals("myFixUser", secretValuesConfig.get("MY_FIX_USER_0"));

        //check secret paths config
        assertEquals(0, secretPathsConfig.size());
    }

    @Test
    void multiLevelConfigTest() {
        Th2Spec spec = new Th2Spec();
        Map<String, Object> customConfig = new HashMap<>();
        Map<String, Object> level1 = new HashMap<>();
        Map<String, Object> level2 = new HashMap<>();

        level2.put("pass2", "${secret_value:myOtherPassword}");
        level2.put("file", "${secret_path:mySecretFile}");

        level1.put("pass1", "${secret_value:myFixPassword}");
        level1.put("user1", "${secret_value:myFixUser}");
        level1.put("level2", level2);

        customConfig.put("file1", "${secret_path:mysecretfile}");
        customConfig.put("level1", level1);


        Map<String, String> secretValuesConfig = new HashMap<>();
        Map<String, String> secretPathsConfig = new HashMap<>();
        spec.setCustomConfig(customConfig);
        generateSecretsConfig(spec, secretValuesConfig, secretPathsConfig);

        customConfig = spec.getCustomConfig();
        level1 = (Map<String, Object>) customConfig.get("level1");
        level2 = (Map<String, Object>) level1.get("level2");
        //check custom config
        assertEquals(2, customConfig.size());
        assertEquals("${MYSECRETFILE_0}", customConfig.get("file1"));


        //check level1
        assertEquals("${MY_FIX_PASSWORD_1}", level1.get("pass1"));
        assertEquals("${MY_FIX_USER_0}", level1.get("user1"));

        ///check level2
        assertEquals("${MY_OTHER_PASSWORD_2}", level2.get("pass2"));
        assertEquals("${MY_SECRET_FILE_1}", level2.get("file"));

        //check secret values config
        assertEquals(3, secretValuesConfig.size());
        assertEquals("myFixPassword", secretValuesConfig.get("MY_FIX_PASSWORD_1"));
        assertEquals("myFixUser", secretValuesConfig.get("MY_FIX_USER_0"));
        assertEquals("myOtherPassword", secretValuesConfig.get("MY_OTHER_PASSWORD_2"));

        //check secret paths config
        assertEquals(2, secretPathsConfig.size());
        assertEquals("mysecretfile", secretPathsConfig.get("MYSECRETFILE_0"));
        assertEquals("mySecretFile", secretPathsConfig.get("MY_SECRET_FILE_1"));

    }
}
