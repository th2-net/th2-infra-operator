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
import com.exactpro.th2.infraoperator.configuration.fields.ChartSpec
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQConfig
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQConfig.Companion.CONFIG_MAP_RABBITMQ_PROP_NAME
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQManagementConfig
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQNamespacePermissions
import com.exactpro.th2.infraoperator.metrics.OperatorMetrics.KEY_DETECTION_TIME
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.BOOK_CONFIG_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.BOOK_NAME_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.CHECKSUM_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.COMPONENT_NAME_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.CONFIG_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.CRADLE_MGR_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.CUSTOM_CONFIG_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.DICTIONARIES_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.DOCKER_IMAGE_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.EXTENDED_SETTINGS_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.GRPC_P2P_CONFIG_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.GRPC_ROUTER_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.IS_JOB_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.LOGGING_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.MQ_QUEUE_CONFIG_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.MQ_ROUTER_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.PROMETHEUS_CONFIG_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.PULL_SECRETS_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.ROOTLESS_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.SCHEMA_SECRETS_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.SECRET_PATHS_CONFIG_ALIAS
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.SECRET_VALUES_CONFIG_ALIAS
import com.exactpro.th2.infraoperator.operator.impl.EstoreHelmTh2Op.EVENT_STORAGE_BOX_ALIAS
import com.exactpro.th2.infraoperator.operator.impl.EstoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS
import com.exactpro.th2.infraoperator.operator.impl.MstoreHelmTh2Op.MESSAGE_STORAGE_BOX_ALIAS
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.BOOK_CONFIG_CM_NAME
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.CRADLE_MANAGER_CM_NAME
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.DEFAULT_BOOK
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.GRPC_ROUTER_CM_NAME
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.LOGGING_CM_NAME
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.MQ_ROUTER_CM_NAME
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease.NAME_LENGTH_LIMIT
import com.exactpro.th2.infraoperator.spec.shared.PrometheusConfiguration
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.createEstoreQueue
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.createMstoreQueue
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext.DIRECT
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext.TOPIC
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext.toExchangeName
import com.exactpro.th2.infraoperator.util.CustomResourceUtils.GIT_COMMIT_HASH
import com.exactpro.th2.infraoperator.util.CustomResourceUtils.hashNameIfNeeded
import com.exactpro.th2.infraoperator.util.ExtractUtils.KEY_SOURCE_HASH
import com.exactpro.th2.infraoperator.util.JsonUtils.JSON_MAPPER
import com.exactpro.th2.infraoperator.util.JsonUtils.YAML_MAPPER
import com.exactpro.th2.infraoperator.util.createKubernetesClient
import com.fasterxml.jackson.module.kotlin.readValue
import com.rabbitmq.http.client.Client
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.LoggerFactory.getLogger
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.k3s.K3sContainer
import org.testcontainers.lifecycle.Startable
import org.testcontainers.utility.DockerImageName
import strikt.api.expectThat
import strikt.assertions.getValue
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS
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
    @Timeout(30_000)
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
        YAML_MAPPER.writeValue(operatorConfig.toFile(), createOperatorConfig(rabbitMQContainer))

        System.setProperty(Config.KUBERNETES_KUBECONFIG_FILE, kubeConfig.absolutePathString())
        System.setProperty(CONFIG_FILE_SYSTEM_PROPERTY, operatorConfig.absolutePathString())

        kubeClient = createKubernetesClient().apply { configureK3s() }
        rabbitMQClient = createRabbitMQClient(rabbitMQContainer)
        controller = Th2CrdController().apply(Th2CrdController::start)

        rabbitMQClient.assertExchange(RABBIT_MQ_TOPIC_EXCHANGE, TOPIC, RABBIT_MQ_V_HOST)
    }

    @AfterAll
    @Timeout(30_000)
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
    @Timeout(30_000)
    fun beforeEach() {
        val gitHash = RESOURCE_GIT_HASH_COUNTER.incrementAndGet().toString()
        kubeClient.createNamespace(TH2_NAMESPACE)
        kubeClient.createRabbitMQSecret(TH2_NAMESPACE, gitHash)
        kubeClient.createRabbitMQAppConfigCfgMap(
            TH2_NAMESPACE,
            gitHash,
            createRabbitMQConfig(rabbitMQContainer, TH2_NAMESPACE)
        )

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
    @Timeout(30_000)
    fun afterEach() {
        kubeClient.deleteNamespace(TH2_NAMESPACE, 1, MINUTES)
        // FIXME: Secret not found "th2-test:Secret/rabbitMQ"

        rabbitMQClient.assertNoQueue(createEstoreQueue(TH2_NAMESPACE), 1, SECONDS)
        rabbitMQClient.assertNoQueue(createMstoreQueue(TH2_NAMESPACE), 1, SECONDS)
        rabbitMQClient.assertNoExchange(toExchangeName(TH2_NAMESPACE))
        rabbitMQClient.assertNoUser(TH2_NAMESPACE)

        kubeClient.awaitNoHelmRelease(TH2_NAMESPACE)
    }

    @Test
    @Timeout(30_000)
    fun `add mstore`() {
        val gitHash = RESOURCE_GIT_HASH_COUNTER.incrementAndGet().toString()
        kubeClient.createTh2Mstore(TH2_NAMESPACE, MESSAGE_STORAGE_BOX_ALIAS, createAnnotations(gitHash, TEST_CONTENT))
        kubeClient.awaitHelmRelease(TH2_NAMESPACE, MESSAGE_STORAGE_BOX_ALIAS)
        // FIXME: estore should have binding
//        println("Bindings: ${rabbitMQClient.getQueueBindings(RABBIT_MQ_V_HOST, createEstoreQueue(TH2_NAMESPACE))}")
    }

    @Test
    @Timeout(30_000)
    fun `add estore`() {
        val gitHash = RESOURCE_GIT_HASH_COUNTER.incrementAndGet().toString()
        kubeClient.createTh2Estore(TH2_NAMESPACE, EVENT_STORAGE_BOX_ALIAS, createAnnotations(gitHash, TEST_CONTENT))
        kubeClient.awaitHelmRelease(TH2_NAMESPACE, EVENT_STORAGE_BOX_ALIAS)
        rabbitMQClient.assertBindings(
            createEstoreQueue(TH2_NAMESPACE),
            RABBIT_MQ_V_HOST,
            setOf(
                "link[$TH2_NAMESPACE:$EVENT_STORAGE_BOX_ALIAS:$EVENT_STORAGE_PIN_ALIAS]",
            )
        )
    }

    /**
     * Max name length is limited by [HelmRelease.NAME_LENGTH_LIMIT]
     */
    @Timeout(30_000)
    @ParameterizedTest
    @ValueSource(strings = ["th2-core-component", "th2-core-component-more-than-$NAME_LENGTH_LIMIT-character"])
    fun `add core component (min configuration)`(name: String) {
        val gitHash = RESOURCE_GIT_HASH_COUNTER.incrementAndGet().toString()
        val spec = """
                imageName: $IMAGE
                imageVersion: $VERSION
                type: th2-rpt-data-provider
        """.trimIndent()

        kubeClient.createTh2CoreBox(
            TH2_NAMESPACE,
            name,
            createAnnotations(gitHash, spec.hashCode().toString()),
            spec
        )
        kubeClient.awaitHelmRelease(TH2_NAMESPACE, hashNameIfNeeded(name)).assertMinCfg(name)
        rabbitMQClient.assertBindings(
            createEstoreQueue(TH2_NAMESPACE),
            RABBIT_MQ_V_HOST,
            setOf(
                "link[$TH2_NAMESPACE:$EVENT_STORAGE_BOX_ALIAS:$EVENT_STORAGE_PIN_ALIAS]",
                "key[$TH2_NAMESPACE:$name:$EVENT_STORAGE_PIN_ALIAS]"
            )
        )
    }

    /**
     * Max name length is limited by [HelmRelease.NAME_LENGTH_LIMIT]
     */
    @Timeout(30_000)
    @ParameterizedTest
    @ValueSource(strings = ["th2-component", "th2-component-more-than-$NAME_LENGTH_LIMIT-character"])
    fun `add component (min configuration)`(name: String) {
        val gitHash = RESOURCE_GIT_HASH_COUNTER.incrementAndGet().toString()
        val spec = """
                imageName: $IMAGE
                imageVersion: $VERSION
                type: th2-codec
        """.trimIndent()

        kubeClient.createTh2Box(
            TH2_NAMESPACE,
            name,
            createAnnotations(gitHash, spec.hashCode().toString()),
            spec
        )
        kubeClient.awaitHelmRelease(TH2_NAMESPACE, hashNameIfNeeded(name)).assertMinCfg(name)
        rabbitMQClient.assertBindings(
            createEstoreQueue(TH2_NAMESPACE),
            RABBIT_MQ_V_HOST,
            setOf(
                "link[$TH2_NAMESPACE:$EVENT_STORAGE_BOX_ALIAS:$EVENT_STORAGE_PIN_ALIAS]",
                "key[$TH2_NAMESPACE:$name:$EVENT_STORAGE_PIN_ALIAS]"
            )
        )
    }

//    @Test  // FIXME Failure executing: POST at: https://localhost:33114/apis/th2.exactpro.com/v2/th2dictionaries. Message: Not Found.
    @Timeout(30_000)
    fun `add dictionary (short name)`() {
        val gitHash = RESOURCE_GIT_HASH_COUNTER.incrementAndGet().toString()
        val name = "th2-dictionary"
        val spec = """
                data: $DICTIONARY_CONTENT
        """.trimIndent()

        kubeClient.createTh2Dictionary(
            TH2_NAMESPACE,
            name,
            createAnnotations(gitHash, spec.hashCode().toString()),
            spec
        )
        kubeClient.awaitHelmRelease(TH2_NAMESPACE, name).assertMinCfg(name)
    }

//    @Test
    @Timeout(30_000)
    fun `create job`() {
    }

    companion object {
        private val K_LOGGER = KotlinLogging.logger {}

        private val RESOURCE_GIT_HASH_COUNTER = AtomicLong(10_000_000)

        private val RABBITMQ_DOCKER_IMAGE = DockerImageName.parse("rabbitmq:3.12.6-management")
        private val K3S_DOCKER_IMAGE = DockerImageName.parse("rancher/k3s:v1.21.3-k3s1")

        private val CRD_RESOURCE_NAMES =
            setOf(
                "helmreleases-crd.yaml",
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
        private const val RABBIT_MQ_TOPIC_EXCHANGE = "test-global-exchange"

        private const val TH2_PREFIX = "th2-"
        private const val TH2_NAMESPACE = "${TH2_PREFIX}test"
        private const val TH2_BOOK = "test_book"
        private const val CFG_FIELD = "test-cfg-field"
        private const val CFG_VALUE = "test-cfg-value"
        private const val IMAGE = "ghcr.io/th2-net/th2-estore"
        private const val VERSION = "0.0.0"
        private const val DICTIONARY_CONTENT = "test-dictionary-content"

        private const val TEST_CONTENT = "test-content"

        private fun createOperatorConfig(rabbitMQ: RabbitMQContainer) =
            OperatorConfig(
                chart = ChartSpec(),
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
                .forEach { crd ->
                    crd.create()
                    K_LOGGER.info { "Applied CRD: ${crd.get().metadata.name}" }
                }
        }

        private fun loadCrd(resourceName: String): CRD =
            requireNotNull(IntegrationTest::class.java.classLoader.getResource("crds/$resourceName")) {
                "Resource '$resourceName' isn't found"
            }.let(YAML_MAPPER::readValue)

        private fun createAnnotations(
            gitHash: String,
            sourceHash: String,
        ) = mapOf(
            KEY_DETECTION_TIME to System.currentTimeMillis().toString(),
            GIT_COMMIT_HASH to gitHash,
            KEY_SOURCE_HASH to sourceHash.hashCode().toString(),
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

        private fun HelmRelease.assertMinCfg(name: String) {
            expectThat(componentValuesSection) {
                getValue(BOOK_CONFIG_ALIAS).isA<Map<String, Any?>>().and {
                    hasSize(1)
                    getValue(BOOK_NAME_ALIAS) isEqualTo TH2_BOOK
                }
                getValue(CRADLE_MGR_ALIAS).isA<Map<String, Any?>>().and {
                    hasSize(2)
                    getValue(CHECKSUM_ALIAS).isNotNull()
                    getValue(CONFIG_ALIAS).isNull() // FIXME: shouldn't be null
                }
                getValue(CUSTOM_CONFIG_ALIAS).isA<Map<String, Any?>>().isEmpty()
                getValue(DICTIONARIES_ALIAS).isA<List<Any?>>().isEmpty() // FIXME
                getValue(PULL_SECRETS_ALIAS).isA<List<Any?>>().isEmpty() // FIXME
                getValue(EXTENDED_SETTINGS_ALIAS).isA<Map<String, Any?>>().and {
                    isEmpty() // FIXME: add extendedSettings
                }
                getValue(GRPC_P2P_CONFIG_ALIAS).isA<Map<String, Any?>>().and {
                    hasSize(2)
                    getValue("server").isA<Map<String, Any?>>().and {
                        getValue("attributes").isNull() // FIXME: add attributes
                        getValue("host").isNull() // FIXME: add host
                        getValue("port") isEqualTo 8080
                        getValue("workers") isEqualTo 5
                    }
                    getValue("services").isA<Map<String, Any?>>().isEmpty()
                }
                getValue(IS_JOB_ALIAS) isEqualTo false
                getValue(SECRET_PATHS_CONFIG_ALIAS).isA<Map<String, Any?>>().isEmpty()
                getValue(SECRET_VALUES_CONFIG_ALIAS).isA<Map<String, Any?>>().isEmpty()
                getValue(SCHEMA_SECRETS_ALIAS).isA<Map<String, Any?>>().and {
                    getValue("cassandra") isEqualTo "cassandra"
                    getValue("rabbitMQ") isEqualTo "rabbitMQ"
                }
                getValue(GRPC_ROUTER_ALIAS).isA<Map<String, Any?>>().and {
                    hasSize(2)
                    getValue(CHECKSUM_ALIAS).isNotNull()
                    getValue(CONFIG_ALIAS).isNull() // FIXME
                }
                getValue(DOCKER_IMAGE_ALIAS) isEqualTo "$IMAGE:$VERSION"
                getValue(COMPONENT_NAME_ALIAS) isEqualTo name
                getValue(ROOTLESS_ALIAS).isA<Map<String, Any?>>().and {
                    hasSize(1)
                    getValue("enabled") isEqualTo false // FIXME
                }
                getValue(PROMETHEUS_CONFIG_ALIAS).isA<Map<String, Any?>>().and {
                    hasSize(1)
                    getValue("enabled") isEqualTo true // FIXME
                }
                getValue(LOGGING_ALIAS).isA<Map<String, Any?>>().and {
                    hasSize(2)
                    getValue(CHECKSUM_ALIAS).isNotNull()
                    getValue(CONFIG_ALIAS).isNull() // FIXME
                }
                getValue(MQ_ROUTER_ALIAS).isA<Map<String, Any?>>().and {
                    hasSize(2)
                    getValue(CHECKSUM_ALIAS).isNotNull()
                    getValue(CONFIG_ALIAS).isNull() // FIXME
                }
                getValue(MQ_QUEUE_CONFIG_ALIAS).isA<Map<String, Any?>>().and {
                    hasSize(2)
                    getValue("globalNotification").isA<Map<String, Any?>>().and {
                        hasSize(1)
                        getValue("exchange") isEqualTo RABBIT_MQ_TOPIC_EXCHANGE
                    }
                    getValue("queues").isA<Map<String, Any?>>().and {
                        hasSize(1)
                        getValue(EVENT_STORAGE_PIN_ALIAS).isA<Map<String, Any?>>().and {
                            hasSize(5)
                            getValue("attributes").isA<List<String>>() isEqualTo listOf("publish", "event")
                            getValue("exchange") isEqualTo toExchangeName(TH2_NAMESPACE)
                            getValue("filters").isA<List<String>>().isEmpty() // FIXME
                            getValue("name") isEqualTo "key[$TH2_NAMESPACE:$name:$EVENT_STORAGE_PIN_ALIAS]"
                            getValue("queue").isA<String>().isEmpty()
                        }
                    }
                }
            }
        }
    }
}
