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

package com.exactpro.th2.infraoperator.fabric8;

import com.exactpro.th2.infraoperator.fabric8.configuration.OperatorConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestConfiguration {

    @Test
    void testConfig() {
        OperatorConfig.Configuration expected  = new OperatorConfig.Configuration();
        expected.setChartConfig(OperatorConfig.ChartConfig.builder().git("git").path("path").ref("ref").build());
        expected.getMqGlobalConfig().setHost("host");
        expected.getMqGlobalConfig().setPort(8080);
        expected.getMqGlobalConfig().setUsername("username");
        expected.getMqGlobalConfig().setPassword("password");
        expected.getMqGlobalConfig().setPersistence(true);
        expected.getMqGlobalConfig().getSchemaUserPermissions().setConfigure("configure");
        expected.getMqGlobalConfig().getSchemaUserPermissions().setRead("read");
        expected.getMqGlobalConfig().getSchemaUserPermissions().setWrite("write");

        Assertions.assertEquals(expected.getChartConfig(), OperatorConfig.INSTANCE.getChartConfig());
        Assertions.assertEquals(expected.getMqGlobalConfig(), OperatorConfig.INSTANCE.getMqAuthConfig());
    }
}
