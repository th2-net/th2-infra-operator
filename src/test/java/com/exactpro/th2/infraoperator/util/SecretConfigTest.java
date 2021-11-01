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

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.exactpro.th2.infraoperator.util.HelmReleaseUtils.generateSecretsConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SecretConfigTest {

    @Test
    public void singleLevelConfigTest() {
        Map<String, Object> customConfig = new HashMap<>();
        customConfig.put("pass1", "${secret_value:myFixPassword}");
        customConfig.put("pass2", "${secret_value:myOtherPassword}");
        customConfig.put("user1", "${secret_value:myFixUser}");
        customConfig.put("file1", "${secret_path:mySecretFile}");
        customConfig.put("file2", "${secret_path:mySecRetFile}");

        Map<String, String> secretValuesConfig = new HashMap<>();
        Map<String, String> secretPathsConfig = new HashMap<>();
        generateSecretsConfig(customConfig, secretValuesConfig, secretPathsConfig);

        // check custom config
        assertEquals(customConfig.get("pass1"), "${MY_FIX_PASSWORD_1}");
        assertEquals(customConfig.get("pass2"), "${MY_OTHER_PASSWORD_2}");
        assertEquals(customConfig.get("user1"), "${MY_FIX_USER_0}");
        assertEquals(customConfig.get("file1"), "${MY_SECRET_FILE_1}");
        assertEquals(customConfig.get("file2"), "${MY_SEC_RET_FILE_0}");

        //check secret values config
        assertEquals(secretValuesConfig.size(), 3);
        assertEquals(secretValuesConfig.get("MY_FIX_PASSWORD_1"), "myFixPassword");
        assertEquals(secretValuesConfig.get("MY_OTHER_PASSWORD_2"), "myOtherPassword");
        assertEquals(secretValuesConfig.get("MY_FIX_USER_0"), "myFixUser");

        //check secret paths config
        assertEquals(secretPathsConfig.size(), 2);
        assertEquals(secretPathsConfig.get("MY_SECRET_FILE_1"), "mySecretFile");
        assertEquals(secretPathsConfig.get("MY_SEC_RET_FILE_0"), "mySecRetFile");
    }

    @Test
    public void suffixedTextTest() {
        Map<String, Object> customConfig = new HashMap<>();
        customConfig.put("pass1", "${secret_value:myFixPassword}someText");
        customConfig.put("user1", "${secret_value:myFixUser}someOtherText");

        Map<String, String> secretValuesConfig = new HashMap<>();
        Map<String, String> secretPathsConfig = new HashMap<>();
        generateSecretsConfig(customConfig, secretValuesConfig, secretPathsConfig);

        // check custom config
        assertEquals(customConfig.get("pass1"), "${MY_FIX_PASSWORD_1}someText");
        assertEquals(customConfig.get("user1"), "${MY_FIX_USER_0}someOtherText");

        //check secret values config
        assertEquals(secretValuesConfig.size(), 2);
        assertEquals(secretValuesConfig.get("MY_FIX_PASSWORD_1"), "myFixPassword");
        assertEquals(secretValuesConfig.get("MY_FIX_USER_0"), "myFixUser");

        //check secret paths config
        assertEquals(secretPathsConfig.size(), 0);
    }

    @Test
    public void multiLevelConfigTest() {
        Map<String, Object> customConfig = new HashMap<>();
        Map<String, Object> level1 = new HashMap<>();
        Map<String, Object> level2 = new HashMap<>();

        level2.put("pass2", "${secret_value:myOtherPassword}");
        level2.put("file", "${secret_path:mySecretFile}");

        level1.put("pass1", "${secret_value:myFixPassword}");
        level1.put("user1", "${secret_value:myFixUser}");
        level1.put("level2", level2);

        customConfig.put("file1", "${secret_path:mysecretfile}");
        customConfig.put("level2", level1);


        Map<String, String> secretValuesConfig = new HashMap<>();
        Map<String, String> secretPathsConfig = new HashMap<>();
        generateSecretsConfig(customConfig, secretValuesConfig, secretPathsConfig);


        //check custom config
        assertEquals(customConfig.size(), 2);
        assertEquals(customConfig.get("file1"), "${MYSECRETFILE_0}");


        //check level1
        assertEquals(level1.get("pass1"), "${MY_FIX_PASSWORD_1}");
        assertEquals(level1.get("user1"), "${MY_FIX_USER_0}");

        ///check level2
        assertEquals(level2.get("pass2"), "${MY_OTHER_PASSWORD_0}");
        assertEquals(level2.get("file"), "${MY_SECRET_FILE_0}");

        //check secret values config
        assertEquals(secretValuesConfig.size(), 3);
        assertEquals(secretValuesConfig.get("MY_FIX_PASSWORD_1"), "myFixPassword");
        assertEquals(secretValuesConfig.get("MY_FIX_USER_0"), "myFixUser");
        assertEquals(secretValuesConfig.get("MY_OTHER_PASSWORD_0"), "myOtherPassword");

        //check secret paths config
        assertEquals(secretPathsConfig.size(), 2);
        assertEquals(secretPathsConfig.get("MYSECRETFILE_0"), "mysecretfile");
        assertEquals(secretPathsConfig.get("MY_SECRET_FILE_0"), "mySecretFile");

    }
}
