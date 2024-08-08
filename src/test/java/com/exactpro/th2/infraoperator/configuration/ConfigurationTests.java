/*
 * Copyright 2020-2024 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.infraoperator.configuration;

import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQManagementConfig;
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQNamespacePermissions;
import com.exactpro.th2.infraoperator.configuration.fields.SchemaSecrets;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.exactpro.th2.infraoperator.configuration.ConfigLoader.CONFIG_FILE_SYSTEM_PROPERTY;
import static org.junit.jupiter.api.Assertions.*;
import static com.exactpro.th2.infraoperator.configuration.OperatorConfig.*;

class ConfigurationTests {

    private OperatorConfig expected;

    static final String DIR = "src/test/resources/";

    private void beforeEach(String fileName) {
        expected = new OperatorConfig();
        System.setProperty(CONFIG_FILE_SYSTEM_PROPERTY, DIR + fileName);
    }

    private OperatorConfig loadConfiguration() {
        return ConfigLoader.loadConfiguration();
    }

    @Test
    void testFullConfig() {
        beforeEach("fullConfig.yml");
// TODO restore this check
//        expected.setChart(new ChartConfig(
//                new ChartSpec(
//                        "chart",
//                        "1.0.0",
//                        "reconcileStrategy",
//                        new ChartSourceRef("source-kind", "source-name", "namespace"))));
        expected.setRabbitMQManagement(
                new RabbitMQManagementConfig(
                        "host",
                        8080,
                        8080,
                        "th2",
                        "exchange",
                        "username",
                        "password",
                        true,
                        900,
                        new RabbitMQNamespacePermissions(
                                "configure", "read", "write"
                        )
                )
        );
        expected.setSchemaSecrets(new SchemaSecrets("rabbitMQ", "cassandra"));
        expected.setNamespacePrefixes(Arrays.asList("string1", "string2"));
        expected.setRabbitMQConfigMapName("rabbit-mq-app");

        assertEquals(expected, loadConfiguration());
    }

    @Test
    void testNsPrefixes() {
        beforeEach("nsPrefixesConfig.yml");

        expected.setNamespacePrefixes(Arrays.asList("string1", "string2"));

        assertEquals(expected, loadConfiguration());
    }

    @Test
    void testRabbitMQManagementConfig() {
        beforeEach("rabbitMQManagementConfig.yml");

        expected.setRabbitMQManagement(
                new RabbitMQManagementConfig(
                        "host",
                        8080,
                        8080,
                        "th2",
                        "exchange",
                        "username",
                        "password",
                        true,
                        900,
                        new RabbitMQNamespacePermissions(
                                "configure", "read", "write"
                        )
                )
        );

        assertEquals(expected, loadConfiguration());
    }

    @Test
    void testSchemaSecretsConfig() {
        beforeEach("schemaSecretsConfig.yml");

        expected.setSchemaSecrets(new SchemaSecrets("rabbitMQ", "cassandra"));

        assertEquals(expected, loadConfiguration());
    }

    @Test
    void testDefaultConfig() {
        OperatorConfig config = new OperatorConfig();
        assertEquals(Collections.emptyList(),
                config.getNamespacePrefixes());

        assertTrue(config.getRabbitMQManagement().getHost().isEmpty());
        assertEquals(0,
                config.getRabbitMQManagement().getManagementPort());
        assertTrue(config.getRabbitMQManagement().getUsername().isEmpty());
        assertTrue(config.getRabbitMQManagement().getPassword().isEmpty());
        assertFalse(config.getRabbitMQManagement().getPersistence());
        assertEquals(RabbitMQNamespacePermissions.DEFAULT_CONFIGURE_PERMISSION,
                config.getRabbitMQManagement().getSchemaPermissions().getConfigure());
        assertEquals(RabbitMQNamespacePermissions.DEFAULT_READ_PERMISSION,
                config.getRabbitMQManagement().getSchemaPermissions().getRead());
        assertEquals(RabbitMQNamespacePermissions.DEFAULT_WRITE_PERMISSION,
                config.getRabbitMQManagement().getSchemaPermissions().getWrite());

        assertEquals(DEFAULT_RABBITMQ_CONFIGMAP_NAME,
                config.getRabbitMQConfigMapName());

        assertEquals(SchemaSecrets.DEFAULT_RABBITMQ_SECRET,
                config.getSchemaSecrets().getRabbitMQ());
        assertEquals(SchemaSecrets.DEFAULT_CASSANDRA_SECRET,
                config.getSchemaSecrets().getCassandra());
    }
}
