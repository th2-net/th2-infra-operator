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

package com.exactpro.th2.infraoperator.configuration;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static com.exactpro.th2.infraoperator.configuration.OperatorConfig.*;

class ConfigurationTests {

    private OperatorConfig.Configuration expected;

    static final String DIR = "src/test/resources/";

    private void beforeEach(String fileName) {
        expected = new OperatorConfig.Configuration();
        System.setProperty(OperatorConfig.CONFIG_FILE_SYSTEM_PROPERTY, DIR + fileName);
    }

    private Configuration loadConfiguration() {
        return OperatorConfig.INSTANCE.loadConfiguration();
    }

    @Test
    void testFullConfig() {
        beforeEach("fullConfig.yml");

        expected.setChartConfig(ChartConfig.builder().withGit("git").withPath("path").withRef("ref").build());
        expected.setRabbitMQManagementConfig(
                RabbitMQManagementConfig.builder().withHost("host").withManagementPort(8080).withApplicationPort(8080)
                        .withVHostName("th2").withExchangeName("exchange")
                        .withUsername("username").withPassword("password").withPersistence(true)
                        .withRabbitMQNamespacePermissions(new RabbitMQNamespacePermissions(
                                "configure", "read", "write"
                        ))
                        .build()
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
    void testGitChartConfig() {
        beforeEach("gitChartConfig.yml");

        expected.setChartConfig(ChartConfig.builder().withGit("git").withPath("path").withRef("ref").build());

        assertEquals(expected, loadConfiguration());
    }

    @Test
    void testHelmChartConfig() {
        beforeEach("helmChartConfig.yml");

        expected.setChartConfig(ChartConfig.builder().withRepository("helm").withName("name")
                .withVersion("1.1.0").build());

        assertEquals(expected, loadConfiguration());
    }

    @Test
    void testChartConfigValidity() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withGit("git").build()));

        assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withRepository("helm").build()));

        assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withGit("git").withRef("ref").build()));

        assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withGit("git").withRef("ref")
                        .withPath("").build()));

        assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withRepository("helm").withName("")
                        .withVersion("1.1.0").build()));

        assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withGit("git").withRef("ref")
                        .withPath("path").withRepository("helm").build()));

        assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withGit("git").withRef("ref")
                        .withRepository("helm").withName("name").build()));

        assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withRef("ref").withPath("path")
                        .withRepository("helm").withName("name").withVersion("1.1.0").build()));

        assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withGit("git").withRef("ref")
                        .withPath("path").withRepository("helm").withName("name").withVersion("1.1.0").build()));

        assertThrows(IllegalArgumentException.class,
                () -> ChartConfig.builder().withRef("ref").withPath("path").build()
                        .overrideWith(ChartConfig.builder().withGit("git").withVersion("1.1.0").build()));

        assertDoesNotThrow(() -> new ChartConfig().overrideWith(
                ChartConfig.builder().withGit("git").withRef("ref").withPath("path").build()));

        assertDoesNotThrow(() -> new ChartConfig().overrideWith(
                ChartConfig.builder().withRepository("helm").withName("name").withVersion("1.1.0").build()));

        assertDoesNotThrow(() -> new ChartConfig().overrideWith(ChartConfig.builder().build()));

        assertDoesNotThrow(() -> new ChartConfig().overrideWith(
                ChartConfig.builder().withRepository("").withPath("").withName("").build()));

        ChartConfig config = ChartConfig.builder().withGit("git1").withRef("ref1").withPath("path1").build()
                .overrideWith(ChartConfig.builder().withGit("git2").withRef("ref2").withPath("path2").build());
        assertEquals("git2", config.getGit());
        assertEquals("ref2", config.getRef());
        assertEquals("path2", config.getPath());

        config = ChartConfig.builder().withRepository("helm1").withName("name1").withVersion("1.1.1").build()
                .overrideWith(ChartConfig.builder().withRepository("helm2").withName("name2").withVersion("1.1.2")
                        .build());
        assertEquals("helm2", config.getRepository());
        assertEquals("name2", config.getName());
        assertEquals("1.1.2", config.getVersion());

        config = ChartConfig.builder().withGit("git").withRef("ref").withPath("path").build().overrideWith(
                ChartConfig.builder().withRepository("helm").withName("name").withVersion("1.1.0").build());
        assertNull(config.getGit());
        assertNull(config.getRef());
        assertNull(config.getPath());
        assertEquals("helm", config.getRepository());
        assertEquals("name", config.getName());
        assertEquals("1.1.0", config.getVersion());

        config = ChartConfig.builder().withRepository("helm").withName("name").withVersion("1.1.0").build()
                .overrideWith(ChartConfig.builder().build());
        assertNull(config.getGit());
        assertNull(config.getRef());
        assertNull(config.getPath());
        assertEquals("helm", config.getRepository());
        assertEquals("name", config.getName());
        assertEquals("1.1.0", config.getVersion());

        config = ChartConfig.builder().withRef("ref").withPath("path").build()
                .overrideWith(ChartConfig.builder().withGit("git").build());
        assertEquals("git", config.getGit());
        assertEquals("ref", config.getRef());
        assertEquals("path", config.getPath());
    }

    @Test
    void testRabbitMQManagementConfig() {
        beforeEach("rabbitMQManagementConfig.yml");

        expected.setRabbitMQManagementConfig(
                RabbitMQManagementConfig.builder().withHost("host").withManagementPort(8080).withApplicationPort(8080)
                        .withVHostName("th2").withExchangeName("exchange")
                        .withUsername("username").withPassword("password").withPersistence(true)
                        .withRabbitMQNamespacePermissions(new RabbitMQNamespacePermissions(
                                "configure", "read", "write"
                        ))
                        .build()
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
    void testEmptyConfig() {
        beforeEach("emptyConfig.yml");

        assertEquals(expected, loadConfiguration());
    }

    @Test
    void testDefaultConfig() {
        beforeEach("defaultConfig.yml");

        Configuration config = loadConfiguration();
        assertEquals(Collections.emptyList(),
                config.getNamespacePrefixes());

        assertNull(config.getChartConfig().getGit());
        assertNull(config.getChartConfig().getRef());
        assertNull(config.getChartConfig().getPath());

        assertNull(config.getRabbitMQManagementConfig().getHost());
        assertEquals(0,
                config.getRabbitMQManagementConfig().getManagementPort());
        assertNull(config.getRabbitMQManagementConfig().getUsername());
        assertNull(config.getRabbitMQManagementConfig().getPassword());
        assertFalse(config.getRabbitMQManagementConfig()
                .isPersistence());
        assertEquals(RabbitMQNamespacePermissions.DEFAULT_CONFIGURE_PERMISSION,
                config.getRabbitMQManagementConfig().getRabbitMQNamespacePermissions().getConfigure());
        assertEquals(RabbitMQNamespacePermissions.DEFAULT_READ_PERMISSION,
                config.getRabbitMQManagementConfig().getRabbitMQNamespacePermissions().getRead());
        assertEquals(RabbitMQNamespacePermissions.DEFAULT_WRITE_PERMISSION,
                config.getRabbitMQManagementConfig().getRabbitMQNamespacePermissions().getWrite());

        assertEquals(OperatorConfig.DEFAULT_RABBITMQ_CONFIGMAP_NAME,
                config.getRabbitMQConfigMapName());

        assertEquals(SchemaSecrets.DEFAULT_RABBITMQ_SECRET,
                config.getSchemaSecrets().getRabbitMQ());
        assertEquals(SchemaSecrets.DEFAULT_CASSANDRA_SECRET,
                config.getSchemaSecrets().getCassandra());
    }

    @Test
    void testSchemaPermissionsDefaultConfig() {
        beforeEach("schemaPermissionsDefaultConfig.yml");

        assertEquals(new RabbitMQNamespacePermissions(), loadConfiguration()
                .getRabbitMQManagementConfig().getRabbitMQNamespacePermissions());
    }
}
