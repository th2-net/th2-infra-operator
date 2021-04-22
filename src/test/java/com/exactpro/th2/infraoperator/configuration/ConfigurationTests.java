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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

public class ConfigurationTests {

    private OperatorConfig.Configuration expected;

    private void beforeEach(String path) {
        expected = new OperatorConfig.Configuration();
        System.setProperty(OperatorConfig.CONFIG_FILE_SYSTEM_PROPERTY, path);
    }

    @Test
    void testFullConfig() {
        beforeEach("src/test/resources/fullConfig.yml");

        expected.setChartConfig(ChartConfig.builder().withGit("git").withPath("path").withRef("ref").build());
        expected.setRabbitMQManagementConfig(
                RabbitMQManagementConfig.builder().withHost("host").withPort(8080)
                        .withUsername("username").withPassword("password").withPersistence(true)
                        .withRabbitMQNamespacePermissions(RabbitMQNamespacePermissions.builder()
                                .withConfigure("configure").withRead("read").withWrite("write").build())
                        .build()
        );
        expected.setSchemaSecrets(SchemaSecrets.builder().withRabbitMQ("rabbitMQ").withCassandra("cassandra").build());
        expected.setNamespacePrefixes(Arrays.asList("string1", "string2"));
        expected.setRabbitMQConfigMapName("rabbit-mq-app");

        Assertions.assertEquals(expected, OperatorConfig.INSTANCE.loadConfiguration());
    }

    @Test
    void testNsPrefixes() {
        beforeEach("src/test/resources/nsPrefixesConfig.yml");

        expected.setNamespacePrefixes(Arrays.asList("string1", "string2"));

        Assertions.assertEquals(expected, OperatorConfig.INSTANCE.loadConfiguration());
    }

    @Test
    void testGitChartConfig() {
        beforeEach("src/test/resources/gitChartConfig.yml");

        expected.setChartConfig(ChartConfig.builder().withGit("git").withPath("path").withRef("ref").build());

        Assertions.assertEquals(expected, OperatorConfig.INSTANCE.loadConfiguration());
    }

    @Test
    void testHelmChartConfig() {
        beforeEach("src/test/resources/helmChartConfig.yml");

        expected.setChartConfig(ChartConfig.builder().withRepository("helm").withName("name")
                .withVersion("1.1.0").build());

        Assertions.assertEquals(expected, OperatorConfig.INSTANCE.loadConfiguration());
    }

    @Test
    void testChartConfigValidity() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withGit("git").build()));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withRepository("helm").build()));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withGit("git").withRef("ref").build()));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withGit("git").withRef("ref")
                        .withPath("").build()));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withRepository("helm").withName("")
                        .withVersion("1.1.0").build()));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withGit("git").withRef("ref")
                        .withPath("path").withRepository("helm").build()));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withGit("git").withRef("ref")
                        .withRepository("helm").withName("name").build()));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withRef("ref").withPath("path")
                        .withRepository("helm").withName("name").withVersion("1.1.0").build()));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ChartConfig().overrideWith(ChartConfig.builder().withGit("git").withRef("ref")
                        .withPath("path").withRepository("helm").withName("name").withVersion("1.1.0").build()));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> ChartConfig.builder().withRef("ref").withPath("path").build()
                        .overrideWith(ChartConfig.builder().withGit("git").withVersion("1.1.0").build()));

        Assertions.assertDoesNotThrow(() -> new ChartConfig().overrideWith(
                ChartConfig.builder().withGit("git").withRef("ref").withPath("path").build()));

        Assertions.assertDoesNotThrow(() -> new ChartConfig().overrideWith(
                ChartConfig.builder().withRepository("helm").withName("name").withVersion("1.1.0").build()));

        Assertions.assertDoesNotThrow(() -> new ChartConfig().overrideWith(ChartConfig.builder().build()));

        Assertions.assertDoesNotThrow(() -> new ChartConfig().overrideWith(
                ChartConfig.builder().withRepository("").withPath("").withName("").build()));

        ChartConfig config = ChartConfig.builder().withGit("git1").withRef("ref1").withPath("path1").build()
                .overrideWith(ChartConfig.builder().withGit("git2").withRef("ref2").withPath("path2").build());
        Assertions.assertEquals("git2", config.getGit());
        Assertions.assertEquals("ref2", config.getRef());
        Assertions.assertEquals("path2", config.getPath());

        config = ChartConfig.builder().withRepository("helm1").withName("name1").withVersion("1.1.1").build()
                .overrideWith(ChartConfig.builder().withRepository("helm2").withName("name2").withVersion("1.1.2")
                        .build());
        Assertions.assertEquals("helm2", config.getRepository());
        Assertions.assertEquals("name2", config.getName());
        Assertions.assertEquals("1.1.2", config.getVersion());

        config = ChartConfig.builder().withGit("git").withRef("ref").withPath("path").build().overrideWith(
                ChartConfig.builder().withRepository("helm").withName("name").withVersion("1.1.0").build());
        Assertions.assertNull(config.getGit());
        Assertions.assertNull(config.getRef());
        Assertions.assertNull(config.getPath());
        Assertions.assertEquals("helm", config.getRepository());
        Assertions.assertEquals("name", config.getName());
        Assertions.assertEquals("1.1.0", config.getVersion());

        config = ChartConfig.builder().withRepository("helm").withName("name").withVersion("1.1.0").build()
                .overrideWith(ChartConfig.builder().build());
        Assertions.assertNull(config.getGit());
        Assertions.assertNull(config.getRef());
        Assertions.assertNull(config.getPath());
        Assertions.assertEquals("helm", config.getRepository());
        Assertions.assertEquals("name", config.getName());
        Assertions.assertEquals("1.1.0", config.getVersion());

        config = ChartConfig.builder().withRef("ref").withPath("path").build()
                .overrideWith(ChartConfig.builder().withGit("git").build());
        Assertions.assertEquals("git", config.getGit());
        Assertions.assertEquals("ref", config.getRef());
        Assertions.assertEquals("path", config.getPath());
    }

    @Test
    void testRabbitMQManagementConfig() {
        beforeEach("src/test/resources/rabbitMQManagementConfig.yml");

        expected.setRabbitMQManagementConfig(
                RabbitMQManagementConfig.builder().withHost("host").withPort(8080)
                        .withUsername("username").withPassword("password").withPersistence(true)
                        .withRabbitMQNamespacePermissions(RabbitMQNamespacePermissions.builder()
                                .withConfigure("configure").withRead("read").withWrite("write").build())
                        .build()
        );

        Assertions.assertEquals(expected, OperatorConfig.INSTANCE.loadConfiguration());
    }

    @Test
    void testSchemaSecretsConfig() {
        beforeEach("src/test/resources/schemaSecretsConfig.yml");

        expected.setSchemaSecrets(SchemaSecrets.builder().withRabbitMQ("rabbitMQ").withCassandra("cassandra").build());

        Assertions.assertEquals(expected, OperatorConfig.INSTANCE.loadConfiguration());
    }

    @Test
    void testEmptyConfig() {
        beforeEach("src/test/resources/emptyConfig.yml");

        Assertions.assertEquals(expected, OperatorConfig.INSTANCE.loadConfiguration());
    }

    @Test
    void testDefaultConfig() {
        beforeEach("src/test/resources/defaultConfig.yml");

        Assertions.assertEquals(Collections.emptyList(),
                OperatorConfig.INSTANCE.loadConfiguration().getNamespacePrefixes());

        Assertions.assertNull(OperatorConfig.INSTANCE.loadConfiguration().getChartConfig().getGit());
        Assertions.assertNull(OperatorConfig.INSTANCE.loadConfiguration().getChartConfig().getRef());
        Assertions.assertNull(OperatorConfig.INSTANCE.loadConfiguration().getChartConfig().getPath());

        Assertions.assertNull(OperatorConfig.INSTANCE.loadConfiguration().getRabbitMQManagementConfig().getHost());
        Assertions.assertEquals(0,
                OperatorConfig.INSTANCE.loadConfiguration().getRabbitMQManagementConfig().getPort());
        Assertions.assertNull(OperatorConfig.INSTANCE.loadConfiguration().getRabbitMQManagementConfig().getUsername());
        Assertions.assertNull(OperatorConfig.INSTANCE.loadConfiguration().getRabbitMQManagementConfig().getPassword());
        Assertions.assertFalse(OperatorConfig.INSTANCE.loadConfiguration().getRabbitMQManagementConfig()
                .isPersistence());
        Assertions.assertEquals(RabbitMQNamespacePermissions.DEFAULT_CONFIGURE_PERMISSION, OperatorConfig.INSTANCE
                .loadConfiguration().getRabbitMQManagementConfig().getRabbitMQNamespacePermissions().getConfigure());
        Assertions.assertEquals(RabbitMQNamespacePermissions.DEFAULT_READ_PERMISSION, OperatorConfig.INSTANCE
                .loadConfiguration().getRabbitMQManagementConfig().getRabbitMQNamespacePermissions().getRead());
        Assertions.assertEquals(RabbitMQNamespacePermissions.DEFAULT_WRITE_PERMISSION, OperatorConfig.INSTANCE
                .loadConfiguration().getRabbitMQManagementConfig().getRabbitMQNamespacePermissions().getWrite());

        Assertions.assertEquals(OperatorConfig.DEFAULT_RABBITMQ_CONFIGMAP_NAME,
                OperatorConfig.INSTANCE.loadConfiguration().getRabbitMQConfigMapName());

        Assertions.assertEquals(SchemaSecrets.DEFAULT_RABBITMQ_SECRET,
                OperatorConfig.INSTANCE.loadConfiguration().getSchemaSecrets().getRabbitMQ());
        Assertions.assertEquals(SchemaSecrets.DEFAULT_CASSANDRA_SECRET,
                OperatorConfig.INSTANCE.loadConfiguration().getSchemaSecrets().getCassandra());
    }

    @Test
    void testSchemaPermissionsDefaultConfig() {
        beforeEach("src/test/resources/schemaPermissionsDefaultConfig.yml");

        Assertions.assertEquals(new RabbitMQNamespacePermissions(), OperatorConfig.INSTANCE.loadConfiguration()
                .getRabbitMQManagementConfig().getRabbitMQNamespacePermissions());
    }

    @Test
    void testIngressHostConfig() {
        beforeEach("src/test/resources/ingressHostConfig.yml");

        Assertions.assertEquals("ingress", OperatorConfig.INSTANCE.loadConfiguration().getIngressHost());
    }
}
