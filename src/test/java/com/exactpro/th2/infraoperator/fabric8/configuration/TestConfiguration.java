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

package com.exactpro.th2.infraoperator.fabric8.configuration;

import com.exactpro.th2.infraoperator.fabric8.configuration.OperatorConfig.ChartConfig;
import com.exactpro.th2.infraoperator.fabric8.configuration.OperatorConfig.Configuration;
import com.exactpro.th2.infraoperator.fabric8.configuration.OperatorConfig.MqGlobalConfig;
import com.exactpro.th2.infraoperator.fabric8.configuration.OperatorConfig.MqSchemaUserPermissions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class TestConfiguration {

    private Configuration expected;

    private void beforeEach(String path) {
        expected = new Configuration();
        System.setProperty(OperatorConfig.CONFIG_FILE_SYSTEM_PROPERTY, path);
    }

    @Test
    void testFullConfig() {
        beforeEach("src/test/resources/fullConfig.yml");

        expected.setChartConfig(ChartConfig.builder().git("git").path("path").ref("ref").build());
        expected.setMqGlobalConfig(
            MqGlobalConfig.builder().host("host").port(8080).username("username").password("password")
                .persistence(true).schemaUserPermissions(
                MqSchemaUserPermissions.builder().configure("configure").read("read").write("write").build())
                .build()
        );
        expected.setSchemaSecrets(SchemaSecrets.builder().rabbitMQ("rabbitMQ").cassandra("cassandra").build());
        expected.setNamespacePrefixes(Arrays.asList("string1", "string2"));

        Assertions.assertEquals(expected, OperatorConfig.INSTANCE.getConfig());
    }

    @Test
    void testNsPrefixes() {
        beforeEach("src/test/resources/nsPrefixesConfig.yml");

        expected.setNamespacePrefixes(Arrays.asList("string1", "string2"));

        Assertions.assertEquals(expected, OperatorConfig.INSTANCE.getConfig());
    }

    @Test
    void testChartConfig() {
        beforeEach("src/test/resources/chartConfig.yml");

        expected.setChartConfig(ChartConfig.builder().git("git").path("path").ref("ref").build());

        Assertions.assertEquals(expected, OperatorConfig.INSTANCE.getConfig());
    }

    @Test
    void testMqGlobalConfig() {
        beforeEach("src/test/resources/mqGlobalConfig.yml");

        expected.setMqGlobalConfig(
            MqGlobalConfig.builder().host("host").port(8080).username("username").password("password")
                .persistence(true).schemaUserPermissions(
                MqSchemaUserPermissions.builder().configure("configure").read("read").write("write").build())
                .build()
        );

        Assertions.assertEquals(expected, OperatorConfig.INSTANCE.getConfig());
    }

    @Test
    void testSchemaSecretsConfig() {
        beforeEach("src/test/resources/schemaSecretsConfig.yml");

        expected.setSchemaSecrets(SchemaSecrets.builder().rabbitMQ("rabbitMQ").cassandra("cassandra").build());

        Assertions.assertEquals(expected, OperatorConfig.INSTANCE.getConfig());
    }

    @Test
    void testEmptyConfig() {
        beforeEach("src/test/resources/emptyConfig.yml");

        expected.setMqGlobalConfig(MqGlobalConfig.builder().schemaUserPermissions(
            MqSchemaUserPermissions.builder().read("").write("").build()).build());

        Assertions.assertEquals(expected, OperatorConfig.INSTANCE.getConfig());
    }
}