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
import com.exactpro.th2.infraoperator.configuration.OperatorConfig.Companion.DEFAULT_RABBITMQ_CONFIGMAP_NAME
import com.exactpro.th2.infraoperator.configuration.OperatorConfig.Companion.RABBITMQ_SECRET_PASSWORD_KEY
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQConfig
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQConfig.Companion.CONFIG_MAP_RABBITMQ_PROP_NAME
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQManagementConfig
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQNamespacePermissions
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.BOOK_CONFIG_CM_NAME
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.CRADLE_MANAGER_CM_NAME
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.DEFAULT_BOOK
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.GRPC_ROUTER_CM_NAME
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.LOGGING_CM_NAME
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.MQ_ROUTER_CM_NAME
import com.exactpro.th2.infraoperator.spec.shared.PrometheusConfiguration
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.createEstoreQueue
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.createMstoreQueue
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext.DIRECT
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext.TOPIC
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext.toExchangeName
import com.exactpro.th2.infraoperator.util.CustomResourceUtils.GIT_COMMIT_HASH
import com.exactpro.th2.infraoperator.util.ExtractUtils.KEY_SOURCE_HASH
import com.exactpro.th2.infraoperator.util.JsonUtils.JSON_MAPPER
import com.exactpro.th2.infraoperator.util.JsonUtils.YAML_MAPPER
import com.exactpro.th2.infraoperator.util.createKubernetesClient
import com.fasterxml.jackson.module.kotlin.readValue
import com.rabbitmq.http.client.Client
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.NamespaceableResource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
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
import java.util.UUID
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.atomic.AtomicLong
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
    fun beforeAll(
        @TempDir tempDir: Path,
    ) {
        this.tempDir = tempDir
        configDir = tempDir.resolve("cfg")
        kubeConfig = configDir.resolve("kube-config.yaml")
        operatorConfig = configDir.resolve("infra-operator.yml")
        configDir.createDirectories()

        k3sContainer =
            K3sContainer(K3S_DOCKER_IMAGE)
                .withLogConsumer(Slf4jLogConsumer(getLogger("K3S")).withSeparateOutputStreams())
                .also(Startable::start)

        rabbitMQContainer =
            RabbitMQContainer(RABBITMQ_DOCKER_IMAGE)
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

        rabbitMQClient.assertExchange(RABBIT_MQ_TOPIC_EXCHANGE, TOPIC, RABBIT_MQ_V_HOST)
    }

    @AfterAll
    fun afterAll() {
        if (this::kubeClient.isInitialized) {
            kubeClient.close()
        }
        if (this::k3sContainer.isInitialized) {
            k3sContainer.stop()
        }
        if (this::rabbitMQContainer.isInitialized) {
            rabbitMQContainer.stop()
        }
    }

    @BeforeEach
    fun beforeEach() {
        val gitHash = RESOURCE_GIT_HASH_COUNTER.incrementAndGet().toString()
        kubeClient.createNamespace(TH2_NAMESPACE)
        kubeClient.createRabbitMQSecret(TH2_NAMESPACE, gitHash)
        kubeClient.createRabbitMQAppConfigCfgMap(TH2_NAMESPACE, gitHash, createRabbitMQConfig(rabbitMQContainer, TH2_NAMESPACE))

        rabbitMQClient.assertUser(TH2_NAMESPACE, RABBIT_MQ_V_HOST, RABBIT_MQ_NAMESPACE_PERMISSIONS)
        rabbitMQClient.assertExchange(toExchangeName(TH2_NAMESPACE), DIRECT, RABBIT_MQ_V_HOST)
        rabbitMQClient.assertQueue(createMstoreQueue(TH2_NAMESPACE), RABBIT_MQ_QUEUE_CLASSIC_TYPE, RABBIT_MQ_V_HOST)
        rabbitMQClient.assertQueue(createEstoreQueue(TH2_NAMESPACE), RABBIT_MQ_QUEUE_CLASSIC_TYPE, RABBIT_MQ_V_HOST)

        kubeClient.createBookConfigCfgMap(TH2_NAMESPACE, gitHash, TH2_BOOK)
        kubeClient.createLoggingCfgMap(TH2_NAMESPACE, gitHash)
        kubeClient.createMQRouterCfgMap(TH2_NAMESPACE, gitHash)
        kubeClient.createGrpcRouterCfgMap(TH2_NAMESPACE, gitHash)
        kubeClient.createCradleManagerCfgMap(TH2_NAMESPACE, gitHash)
    }

    @AfterEach
    fun afterEach() {
        kubeClient.deleteNamespace(TH2_NAMESPACE, 1, MINUTES)

        rabbitMQClient.assertNoQueue(createEstoreQueue(TH2_NAMESPACE))
        rabbitMQClient.assertNoQueue(createMstoreQueue(TH2_NAMESPACE))
        rabbitMQClient.assertNoExchange(toExchangeName(TH2_NAMESPACE))
        rabbitMQClient.assertNoUser(TH2_NAMESPACE)
    }

    @Test
    fun `add mstore`() {
//        val controller = Th2CrdController()
//        controller.start()
//        Thread.sleep(1_000)

//        createKubernetesClient().use { client ->
//            println("NAME_SPACE ${client.namespaces().list().items.map{ it.metadata.name }}")
//        }
    }

    @Test
    fun `add estore`() {
//        val controller = Th2CrdController()
//        controller.start()
//        Thread.sleep(1_000)

//        createKubernetesClient().use { client ->
//            println("NAME_SPACE ${client.namespaces().list().items.map{ it.metadata.name }}")
//        }
    }

    @Test
    fun `add core component`() {
//        val controller = Th2CrdController()
//        controller.start()
//        Thread.sleep(1_000)

//        createKubernetesClient().use { client ->
//            println("NAME_SPACE ${client.namespaces().list().items.map{ it.metadata.name }}")
//        }
    }

    @Test
    fun `add component`() {
//        val controller = Th2CrdController()
//        controller.start()
//        Thread.sleep(1_000)

//        createKubernetesClient().use { client ->
//            println("NAME_SPACE ${client.namespaces().list().items.map{ it.metadata.name }}")
//        }
    }

    companion object {
        private val K_LOGGER = KotlinLogging.logger {}

        private val RESOURCE_GIT_HASH_COUNTER = AtomicLong(10_000_000)

        private val RABBITMQ_DOCKER_IMAGE = DockerImageName.parse("rabbitmq:3.12.6-management")
        private val K3S_DOCKER_IMAGE = DockerImageName.parse("rancher/k3s:v1.21.3-k3s1")

        private val CRD_RESOURCE_NAMES =
            setOf(
                "th2-box-crd.yaml",
                "th2-core-box-crd.yaml",
                "th2-dictionary-crd.yaml",
                "th2-estore-crd.yaml",
                "th2-job-crd.yaml",
                "th2-mstore-crd.yaml",
            )

        private val RABBIT_MQ_NAMESPACE_PERMISSIONS = RabbitMQNamespacePermissions()
        private const val RABBIT_MQ_QUEUE_CLASSIC_TYPE = "classic"
        private const val RABBIT_MQ_V_HOST = "/"
        private const val RABBIT_MQ_TOPIC_EXCHANGE = "global-exchange"

        private const val TH2_PREFIX = "th2-"
        private const val TH2_NAMESPACE = "${TH2_PREFIX}test"
        private const val TH2_BOOK = "test_book"

        private const val TEST_CONTENT = "test-content"

        private fun createConfig(rabbitMQ: RabbitMQContainer) =
            OperatorConfig(
                namespacePrefixes = setOf(TH2_PREFIX),
                rabbitMQManagement =
                    RabbitMQManagementConfig(
                        host = rabbitMQ.host,
                        managementPort = rabbitMQ.httpPort,
                        applicationPort = rabbitMQ.amqpPort,
                        vhostName = RABBIT_MQ_V_HOST,
                        exchangeName = RABBIT_MQ_TOPIC_EXCHANGE,
                        username = rabbitMQ.adminUsername,
                        password = rabbitMQ.adminPassword,
                        persistence = true,
                        schemaPermissions = RABBIT_MQ_NAMESPACE_PERMISSIONS,
                    ),
                prometheusConfiguration =
                    PrometheusConfiguration(
                        "0.0.0.0",
                        "9752",
                        false.toString(),
                    ),
            )

        private fun createRabbitMQConfig(
            rabbitMQ: RabbitMQContainer,
            namespace: String,
        ) = RabbitMQConfig(
            rabbitMQ.amqpPort,
            rabbitMQ.host,
            RABBIT_MQ_V_HOST,
            toExchangeName(namespace),
            namespace,
            "${'$'}{RABBITMQ_PASS}",
        )

        private fun createRabbitMQClient(rabbitMQ: RabbitMQContainer) =
            RabbitMQContext.createClient(
                rabbitMQ.host,
                rabbitMQ.httpPort,
                rabbitMQ.adminUsername,
                rabbitMQ.adminPassword,
            )

        private fun KubernetesClient.configureK3s() {
            CRD_RESOURCE_NAMES
                .asSequence()
                .map(Companion::loadCrd)
                .map(this::resource)
                .forEach(NamespaceableResource<CRD>::create)
        }

        private fun loadCrd(resourceName: String): CRD =
            requireNotNull(IntegrationTest::class.java.classLoader.getResource("crds/$resourceName")) {
                "Resource '$resourceName' isn't found"
            }.let(YAML_MAPPER::readValue)

        private fun createAnnotations(
            gitHash: String,
            content: String,
        ) = mapOf(
            GIT_COMMIT_HASH to gitHash,
            KEY_SOURCE_HASH to content.hashCode().toString(),
        )

        private fun KubernetesClient.createRabbitMQSecret(
            namespace: String,
            gitHash: String,
        ) {
            createSecret(
                namespace,
                "rabbitmq",
                createAnnotations(gitHash, TEST_CONTENT),
                mapOf(RABBITMQ_SECRET_PASSWORD_KEY to UUID.randomUUID().toString()),
            )
        }

        private fun KubernetesClient.deleteRabbitMQSecret(namespace: String) {
            deleteSecret(namespace, "rabbitmq")
        }

        private fun KubernetesClient.createRabbitMQAppConfigCfgMap(
            namespace: String,
            gitHash: String,
            data: RabbitMQConfig,
        ) {
            val content = JSON_MAPPER.writeValueAsString(data)
            createConfigMap(
                namespace,
                DEFAULT_RABBITMQ_CONFIGMAP_NAME,
                createAnnotations(gitHash, content),
                mapOf(CONFIG_MAP_RABBITMQ_PROP_NAME to content),
            )
        }

        private fun KubernetesClient.deleteRabbitMQAppConfigCfgMap(namespace: String) {
            deleteConfigMap(namespace, DEFAULT_RABBITMQ_CONFIGMAP_NAME)
        }

        private fun KubernetesClient.createBookConfigCfgMap(
            namespace: String,
            gitHash: String,
            book: String,
        ) {
            createConfigMap(
                namespace,
                BOOK_CONFIG_CM_NAME,
                createAnnotations(gitHash, book),
                mapOf(DEFAULT_BOOK to book),
            )
        }

        private fun KubernetesClient.deleteBookConfigCfgMap(namespace: String) {
            deleteConfigMap(namespace, BOOK_CONFIG_CM_NAME)
        }

        private fun KubernetesClient.createLoggingCfgMap(
            namespace: String,
            gitHash: String,
        ) {
            createConfigMap(
                namespace,
                LOGGING_CM_NAME,
                createAnnotations(gitHash, TEST_CONTENT),
                mapOf("log4j2.properties" to TEST_CONTENT),
            )
        }

        private fun KubernetesClient.deleteLoggingCfgMap(namespace: String) {
            deleteConfigMap(namespace, LOGGING_CM_NAME)
        }

        private fun KubernetesClient.createMQRouterCfgMap(
            namespace: String,
            gitHash: String,
        ) {
            createConfigMap(
                namespace,
                MQ_ROUTER_CM_NAME,
                createAnnotations(gitHash, TEST_CONTENT),
                mapOf("mq_router.json" to TEST_CONTENT),
            )
        }

        private fun KubernetesClient.deleteMQRouterCfgMap(namespace: String) {
            deleteConfigMap(namespace, MQ_ROUTER_CM_NAME)
        }

        private fun KubernetesClient.createGrpcRouterCfgMap(
            namespace: String,
            gitHash: String,
        ) {
            createConfigMap(
                namespace,
                GRPC_ROUTER_CM_NAME,
                createAnnotations(gitHash, TEST_CONTENT),
                mapOf("grpc_router.json" to TEST_CONTENT),
            )
        }

        private fun KubernetesClient.deleteGrpcRouterCfgMap(namespace: String) {
            deleteConfigMap(namespace, GRPC_ROUTER_CM_NAME)
        }

        private fun KubernetesClient.createCradleManagerCfgMap(
            namespace: String,
            gitHash: String,
        ) {
            createConfigMap(
                namespace,
                CRADLE_MANAGER_CM_NAME,
                createAnnotations(gitHash, TEST_CONTENT),
                mapOf("cradle_manager.json" to TEST_CONTENT),
            )
        }

        private fun KubernetesClient.deleteCradleManagerCfgMap(namespace: String) {
            deleteConfigMap(namespace, CRADLE_MANAGER_CM_NAME)
        }
    }
}
