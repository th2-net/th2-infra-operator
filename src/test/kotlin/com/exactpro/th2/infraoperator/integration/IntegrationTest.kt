/*
 * Copyright 2024-2024 Exactpro (Exactpro Systems Limited)
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
package com.exactpro.th2.infraoperator.integration

import com.exactpro.th2.infraoperator.Th2CrdController
import com.exactpro.th2.infraoperator.configuration.ConfigLoader.CONFIG_FILE_SYSTEM_PROPERTY
import com.exactpro.th2.infraoperator.configuration.OperatorConfig
import com.exactpro.th2.infraoperator.configuration.OperatorConfig.Companion.RABBITMQ_SECRET_PASSWORD_KEY
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQConfig
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQManagementConfig
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQNamespacePermissions
import com.exactpro.th2.infraoperator.spec.shared.PrometheusConfiguration
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.createEstoreQueue
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.createMstoreQueue
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext.DIRECT
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext.TOPIC
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext.toExchangeName
import com.exactpro.th2.infraoperator.util.JsonUtils.YAML_MAPPER
import com.exactpro.th2.infraoperator.util.createKubernetesClient
import com.fasterxml.jackson.module.kotlin.readValue
import com.rabbitmq.http.client.Client
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.NamespaceableResource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory.getLogger
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.k3s.K3sContainer
import org.testcontainers.lifecycle.Startable
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition as CRD

@Tag("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationTest {

    private lateinit var tempDir: Path

    private lateinit var configDir: Path

    private lateinit var kubeConfig: Path

    private lateinit var operatorConfig: Path

    private lateinit var k3sContainer: K3sContainer

    private lateinit var rabbitMQContainer: RabbitMQContainer

    private lateinit var kubeClient: KubernetesClient

    private lateinit var rabbitMQClient: Client

    private lateinit var controller: Th2CrdController


    @BeforeAll
    fun beforeAll(@TempDir tempDir: Path) {
        this.tempDir = tempDir
        configDir = tempDir.resolve("cfg")
        kubeConfig = configDir.resolve("kube-config.yaml")
        operatorConfig = configDir.resolve("infra-operator.yml")
        configDir.createDirectories()

        k3sContainer = K3sContainer(K3S_DOCKER_IMAGE)
            .withLogConsumer(Slf4jLogConsumer(getLogger("K3S")).withSeparateOutputStreams())
            .also(Startable::start)

        rabbitMQContainer = RabbitMQContainer(RABBITMQ_DOCKER_IMAGE)
            .withLogConsumer(Slf4jLogConsumer(getLogger("RABBIT_MQ")).withSeparateOutputStreams())
            .also(Startable::start)

        K_LOGGER.info { "RabbitMQ URL: ${rabbitMQContainer.httpUrl}" }

        println(k3sContainer.kubeConfigYaml)
        Files.writeString(kubeConfig, k3sContainer.kubeConfigYaml)
        YAML_MAPPER.writeValue(operatorConfig.toFile(), createConfig(rabbitMQContainer))

        System.setProperty(Config.KUBERNETES_KUBECONFIG_FILE, kubeConfig.absolutePathString())
        System.setProperty(CONFIG_FILE_SYSTEM_PROPERTY, operatorConfig.absolutePathString())

        kubeClient = createKubernetesClient().apply { configureK3s() }
        rabbitMQClient = createRabbitMQClient(rabbitMQContainer)
        controller = Th2CrdController().apply(Th2CrdController::start)

        rabbitMQClient.assertExchange(TOPIC_EXCHANGE, TOPIC, V_HOST)
    }

    @AfterAll
    fun afterAll() {
        if(this::kubeClient.isInitialized) {
            kubeClient.close()
        }
        if(this::k3sContainer.isInitialized) {
            k3sContainer.stop()
        }
        if(this::rabbitMQContainer.isInitialized) {
            rabbitMQContainer.stop()
        }
    }

    @Test
    fun `create namespace`() {
        val namespace = "${PREFIX}test"
        kubeClient.createNamespace(namespace)
        kubeClient.createSecret("rabbitmq", namespace, mapOf(RABBITMQ_SECRET_PASSWORD_KEY to "test-pass"))
        kubeClient.createRabbitMQAppConfigCfgMap(
            namespace,
            createRabbitMQConfig(rabbitMQContainer, namespace)
        )

        rabbitMQClient.assertUser(namespace, V_HOST, RABBIT_MQ_NAMESPACE_PERMISSIONS)
        rabbitMQClient.assertExchange(toExchangeName(namespace), DIRECT, V_HOST)
        rabbitMQClient.assertQueue(createMstoreQueue(namespace), QUEUE_CLASSIC_TYPE, V_HOST)
        rabbitMQClient.assertQueue(createEstoreQueue(namespace), QUEUE_CLASSIC_TYPE, V_HOST)
    }

    @Test
    fun test() {
//        val controller = Th2CrdController()
//        controller.start()
//        Thread.sleep(5_000)

//        createKubernetesClient().use { client ->
//            println("NAME_SPACE ${client.namespaces().list().items.map{ it.metadata.name }}")
//        }
    }

    companion object {
        private val K_LOGGER = KotlinLogging.logger {}

        private val CRD_RESOURCE_NAME = setOf(
            "th2-box-crd.yaml",
            "th2-core-box-crd.yaml",
            "th2-dictionary-crd.yaml",
            "th2-estore-crd.yaml",
            "th2-job-crd.yaml",
            "th2-mstore-crd.yaml",
        )
        private val RABBIT_MQ_NAMESPACE_PERMISSIONS = RabbitMQNamespacePermissions()

        private const val QUEUE_CLASSIC_TYPE = "classic"
        private const val PREFIX = "th2-"
        private const val V_HOST = "/"
        private const val TOPIC_EXCHANGE = "global-exchange"

        private val RABBITMQ_DOCKER_IMAGE = DockerImageName.parse("rabbitmq:3.12.6-management")
        private val K3S_DOCKER_IMAGE = DockerImageName.parse("rancher/k3s:v1.21.3-k3s1")

        private fun createConfig(rabbitMQ: RabbitMQContainer) = OperatorConfig(
            namespacePrefixes = setOf(PREFIX),
            rabbitMQManagement = RabbitMQManagementConfig(
                host = rabbitMQ.host,
                managementPort = rabbitMQ.httpPort,
                applicationPort = rabbitMQ.amqpPort,
                vhostName = V_HOST,
                exchangeName = TOPIC_EXCHANGE,
                username = rabbitMQ.adminUsername,
                password = rabbitMQ.adminPassword,
                persistence = true,
                schemaPermissions = RABBIT_MQ_NAMESPACE_PERMISSIONS,
            ),
            prometheusConfiguration = PrometheusConfiguration(
                "0.0.0.0",
                "9752",
                false.toString(),
            ),
        )

        private fun createRabbitMQConfig(rabbitMQ: RabbitMQContainer, namespace: String) = RabbitMQConfig(
            rabbitMQ.amqpPort,
            rabbitMQ.host,
            V_HOST,
            toExchangeName(namespace),
            namespace,
            "${'$'}{RABBITMQ_PASS}"
        )

        private fun createRabbitMQClient(rabbitMQ: RabbitMQContainer) = RabbitMQContext.createClient(
            rabbitMQ.host,
            rabbitMQ.httpPort,
            rabbitMQ.adminUsername,
            rabbitMQ.adminPassword,
        )

        private fun KubernetesClient.configureK3s() {
            CRD_RESOURCE_NAME.asSequence()
                .map(Companion::loadCrd)
                .map(this::resource)
                .forEach(NamespaceableResource<CRD>::create)
        }

        private fun loadCrd(resourceName: String): CRD =
            requireNotNull(IntegrationTest::class.java.classLoader.getResource("crds/$resourceName")) {
                "Resource '$resourceName' isn't found"
            }.let(YAML_MAPPER::readValue)
    }
}
