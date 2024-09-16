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
import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.EVENT_STORAGE_BOX_ALIAS
import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS
import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.MESSAGE_STORAGE_BOX_ALIAS
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.BOOK_CONFIG_CM_NAME
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.CRADLE_MANAGER_CM_NAME
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.DEFAULT_BOOK
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.GRPC_ROUTER_CM_NAME
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.LOGGING_CM_NAME
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.MQ_ROUTER_CM_NAME
import com.exactpro.th2.infraoperator.operator.manager.impl.Th2DictionaryEventHandler.DICTIONARY_SUFFIX
import com.exactpro.th2.infraoperator.spec.Th2CustomResource
import com.exactpro.th2.infraoperator.spec.box.Th2Box
import com.exactpro.th2.infraoperator.spec.corebox.Th2CoreBox
import com.exactpro.th2.infraoperator.spec.estore.Th2Estore
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease.NAME_LENGTH_LIMIT
import com.exactpro.th2.infraoperator.spec.job.Th2Job
import com.exactpro.th2.infraoperator.spec.mstore.Th2Mstore
import com.exactpro.th2.infraoperator.spec.shared.PrometheusConfiguration
import com.exactpro.th2.infraoperator.spec.shared.status.RolloutPhase.DISABLED
import com.exactpro.th2.infraoperator.spec.shared.status.RolloutPhase.SUCCEEDED
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.createEstoreQueue
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.createMstoreQueue
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext.DIRECT
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext.TOPIC
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext.toExchangeName
import com.exactpro.th2.infraoperator.util.CustomResourceUtils.hashNameIfNeeded
import com.exactpro.th2.infraoperator.util.JsonUtils.JSON_MAPPER
import com.exactpro.th2.infraoperator.util.JsonUtils.YAML_MAPPER
import com.exactpro.th2.infraoperator.util.createKubernetesClient
import com.fasterxml.jackson.module.kotlin.readValue
import com.rabbitmq.http.client.Client
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
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
    fun beforeAll(@TempDir tempDir: Path) {
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

        rabbitMQClient.assertNoQueue(createEstoreQueue(TH2_NAMESPACE))
        rabbitMQClient.assertNoQueue(createMstoreQueue(TH2_NAMESPACE))
        rabbitMQClient.assertNoExchange(toExchangeName(TH2_NAMESPACE))
        rabbitMQClient.assertNoUser(TH2_NAMESPACE)

        kubeClient.awaitNoResources<HelmRelease>(TH2_NAMESPACE)
        kubeClient.awaitNoResources<ConfigMap>(TH2_NAMESPACE)
        kubeClient.awaitNoResources<Th2Estore>(TH2_NAMESPACE)
        kubeClient.awaitNoResources<Th2Mstore>(TH2_NAMESPACE)
        kubeClient.awaitNoResources<Th2CoreBox>(TH2_NAMESPACE)
        kubeClient.awaitNoResources<Th2Box>(TH2_NAMESPACE)
    }

    interface StoreComponentTest {
        fun `add component`()
    }

    abstract inner class ComponentTest<T: Th2CustomResource> {
        abstract val resourceClass: Class<T>
        abstract val specType: String
        abstract val runAsJob: Boolean
        abstract fun createResources(): T

        abstract fun add(name: String)
        abstract fun disable(name: String)
        abstract fun enable(name: String)

        protected fun addTest(name: String) {
            val gitHash = RESOURCE_GIT_HASH_COUNTER.incrementAndGet().toString()
            val spec = """
                imageName: $IMAGE
                imageVersion: $VERSION
                type: $specType
            """.trimIndent()

            kubeClient.createTh2CustomResource(TH2_NAMESPACE, name, gitHash, spec, this::createResources)
            kubeClient.awaitPhase(TH2_NAMESPACE, name, SUCCEEDED, resourceClass)
            kubeClient.awaitResource<HelmRelease>(TH2_NAMESPACE, hashNameIfNeeded(name)).assertMinCfg(name, runAsJob)
            rabbitMQClient.assertBindings(
                createEstoreQueue(TH2_NAMESPACE),
                RABBIT_MQ_V_HOST,
                setOf(
                    "link[$TH2_NAMESPACE:$EVENT_STORAGE_BOX_ALIAS:$EVENT_STORAGE_PIN_ALIAS]",
                    "key[$TH2_NAMESPACE:$name:$EVENT_STORAGE_PIN_ALIAS]"
                )
            )
        }

        protected fun disableTest(name: String) {
            var gitHash = RESOURCE_GIT_HASH_COUNTER.incrementAndGet().toString()
            var spec = """
                imageName: $IMAGE
                imageVersion: $VERSION
                type: $specType
                disabled: false
            """.trimIndent()

            kubeClient.createTh2CustomResource(TH2_NAMESPACE, name, gitHash, spec, this::createResources)
            kubeClient.awaitPhase(TH2_NAMESPACE, name, SUCCEEDED, resourceClass)
            kubeClient.awaitResource<HelmRelease>(TH2_NAMESPACE, hashNameIfNeeded(name)).assertMinCfg(name, runAsJob)
            rabbitMQClient.assertBindings(
                createEstoreQueue(TH2_NAMESPACE),
                RABBIT_MQ_V_HOST,
                setOf(
                    "link[$TH2_NAMESPACE:$EVENT_STORAGE_BOX_ALIAS:$EVENT_STORAGE_PIN_ALIAS]",
                    "key[$TH2_NAMESPACE:$name:$EVENT_STORAGE_PIN_ALIAS]"
                )
            )

            gitHash = RESOURCE_GIT_HASH_COUNTER.incrementAndGet().toString()
            spec = """
                imageName: $IMAGE
                imageVersion: $VERSION
                type: $specType
                disabled: true
            """.trimIndent()

            kubeClient.modifyTh2CustomResource(TH2_NAMESPACE, name, gitHash, spec, resourceClass)
            kubeClient.awaitPhase(TH2_NAMESPACE, name, DISABLED, resourceClass)
            kubeClient.awaitNoResource<HelmRelease>(TH2_NAMESPACE, name)
            rabbitMQClient.assertBindings(
                createEstoreQueue(TH2_NAMESPACE),
                RABBIT_MQ_V_HOST,
                setOf(
                    "link[$TH2_NAMESPACE:$EVENT_STORAGE_BOX_ALIAS:$EVENT_STORAGE_PIN_ALIAS]",
                    "key[$TH2_NAMESPACE:$name:$EVENT_STORAGE_PIN_ALIAS]"
                )
            )
        }

        protected fun enableTest(name: String) {
            var gitHash = RESOURCE_GIT_HASH_COUNTER.incrementAndGet().toString()
            var spec = """
                imageName: $IMAGE
                imageVersion: $VERSION
                type: $specType
                disabled: true
            """.trimIndent()

            kubeClient.createTh2CustomResource(TH2_NAMESPACE, name, gitHash, spec, this::createResources)
            kubeClient.awaitPhase(TH2_NAMESPACE, name, DISABLED, resourceClass)
            kubeClient.awaitNoResource<HelmRelease>(TH2_NAMESPACE, name)
            rabbitMQClient.assertBindings(
                createEstoreQueue(TH2_NAMESPACE),
                RABBIT_MQ_V_HOST,
                setOf("link[$TH2_NAMESPACE:$EVENT_STORAGE_BOX_ALIAS:$EVENT_STORAGE_PIN_ALIAS]")
            )

            gitHash = RESOURCE_GIT_HASH_COUNTER.incrementAndGet().toString()
            spec = """
                imageName: $IMAGE
                imageVersion: $VERSION
                type: $specType
                disabled: false
            """.trimIndent()

            kubeClient.modifyTh2CustomResource(TH2_NAMESPACE, name, gitHash, spec, resourceClass)
            kubeClient.awaitPhase(TH2_NAMESPACE, name, SUCCEEDED, resourceClass)
            kubeClient.awaitResource<HelmRelease>(TH2_NAMESPACE, hashNameIfNeeded(name)).assertMinCfg(name, runAsJob)
            rabbitMQClient.assertBindings(
                createEstoreQueue(TH2_NAMESPACE),
                RABBIT_MQ_V_HOST,
                setOf(
                    "link[$TH2_NAMESPACE:$EVENT_STORAGE_BOX_ALIAS:$EVENT_STORAGE_PIN_ALIAS]",
                    "key[$TH2_NAMESPACE:$name:$EVENT_STORAGE_PIN_ALIAS]"
                )
            )
        }
    }

    @Nested
    inner class Mstore: StoreComponentTest {

        @Test
        @Timeout(30_000)
        override fun `add component`() {
            val gitHash = RESOURCE_GIT_HASH_COUNTER.incrementAndGet().toString()
            val spec = """
                imageName: ghcr.io/th2-net/th2-mstore
                imageVersion: 0.0.0
            """.trimIndent()
            kubeClient.createTh2CustomResource(TH2_NAMESPACE, MESSAGE_STORAGE_BOX_ALIAS, gitHash, spec, ::Th2Mstore)
            kubeClient.awaitPhase<Th2Mstore>(TH2_NAMESPACE, MESSAGE_STORAGE_BOX_ALIAS, SUCCEEDED)
            kubeClient.awaitResource<HelmRelease>(TH2_NAMESPACE, MESSAGE_STORAGE_BOX_ALIAS)
            // FIXME: estore should have binding
//        println("Bindings: ${rabbitMQClient.getQueueBindings(RABBIT_MQ_V_HOST, createEstoreQueue(TH2_NAMESPACE))}")
        }
    }

    @Nested
    inner class Estore: StoreComponentTest {
        @Test
        @Timeout(30_000)
        override fun `add component`() {
            val gitHash = RESOURCE_GIT_HASH_COUNTER.incrementAndGet().toString()
            val spec = """
                imageName: ghcr.io/th2-net/th2-estore
                imageVersion: 0.0.0
            """.trimIndent()
            kubeClient.createTh2CustomResource(TH2_NAMESPACE, EVENT_STORAGE_BOX_ALIAS, gitHash, spec, ::Th2Estore)
            kubeClient.awaitPhase<Th2Estore>(TH2_NAMESPACE, EVENT_STORAGE_BOX_ALIAS, SUCCEEDED)
            kubeClient.awaitResource<HelmRelease>(TH2_NAMESPACE, EVENT_STORAGE_BOX_ALIAS)
            rabbitMQClient.assertBindings(
                createEstoreQueue(TH2_NAMESPACE),
                RABBIT_MQ_V_HOST,
                setOf(
                    "link[$TH2_NAMESPACE:$EVENT_STORAGE_BOX_ALIAS:$EVENT_STORAGE_PIN_ALIAS]",
                )
            )
        }
    }

    @Nested
    inner class CoreComponent: ComponentTest<Th2CoreBox>() {
        override val resourceClass: Class<Th2CoreBox>
            get() = Th2CoreBox::class.java
        override val specType: String
            get() = "th2-rpt-data-provider"
        override val runAsJob: Boolean
            get() = false

        override fun createResources(): Th2CoreBox = Th2CoreBox()

        @Timeout(30_000)
        @ParameterizedTest
        @ValueSource(strings = ["th2-core-component", "th2-core-component-more-than-$NAME_LENGTH_LIMIT-characters"])
        override fun add(name: String) = addTest(name)

        @Timeout(30_000)
        @ParameterizedTest
        @ValueSource(strings = ["th2-core-component", "th2-core-component-more-than-$NAME_LENGTH_LIMIT-characters"])
        override fun disable(name: String) = disableTest(name)

        @Timeout(30_000)
        @ParameterizedTest
        @ValueSource(strings = ["th2-core-component", "th2-core-component-more-than-$NAME_LENGTH_LIMIT-characters"])
        override fun enable(name: String) = enableTest(name)
    }

    @Nested
    inner class Component: ComponentTest<Th2Box>() {
        override val resourceClass: Class<Th2Box>
            get() = Th2Box::class.java
        override val specType: String
            get() = "th2-codec"
        override val runAsJob: Boolean
            get() = false

        override fun createResources(): Th2Box = Th2Box()

        @Timeout(30_000)
        @ParameterizedTest
        @ValueSource(strings = ["th2-component", "th2-component-more-than-$NAME_LENGTH_LIMIT-characters"])
        override fun add(name: String) = addTest(name)

        @Timeout(30_000)
        @ParameterizedTest
        @ValueSource(strings = ["th2-component", "th2-component-more-than-$NAME_LENGTH_LIMIT-characters"])
        override fun disable(name: String) = disableTest(name)

        @Timeout(30_000)
        @ParameterizedTest
        @ValueSource(strings = ["th2-component", "th2-component-more-than-$NAME_LENGTH_LIMIT-characters"])
        override fun enable(name: String) = enableTest(name)
    }

    @Nested
    inner class Job: ComponentTest<Th2Job>() {
        override val resourceClass: Class<Th2Job>
            get() = Th2Job::class.java
        override val specType: String
            get() = "th2-job" // supported values: "th2-job"
        override val runAsJob: Boolean
            get() = true

        override fun createResources(): Th2Job = Th2Job()

        @Timeout(30_000)
        @ParameterizedTest
        @ValueSource(strings = ["th2-job", "th2-job-more-than-$NAME_LENGTH_LIMIT-characters"])
        override fun add(name: String) = addTest(name)

        @Timeout(30_000)
        @ParameterizedTest
        @ValueSource(strings = ["th2-job", "th2-job-more-than-$NAME_LENGTH_LIMIT-characters"])
        override fun disable(name: String) = disableTest(name)

        @Timeout(30_000)
        @ParameterizedTest
        @ValueSource(strings = ["th2-job", "th2-job-more-than-$NAME_LENGTH_LIMIT-characters"])
        override fun enable(name: String) = enableTest(name)
    }

    @Nested
    inner class Dictionary {

        @Test
        @Timeout(30_000)
        fun `add dictionary (short name)`() {
            val gitHash = RESOURCE_GIT_HASH_COUNTER.incrementAndGet().toString()
            val name = "th2-dictionary"
            val spec = """
                data: $DICTIONARY_CONTENT
        """.trimIndent()

            val annotations = createAnnotations(gitHash, spec.hashCode().toString())
            kubeClient.createTh2Dictionary(
                TH2_NAMESPACE,
                name,
                annotations,
                spec
            )
            kubeClient.awaitResource<ConfigMap>(TH2_NAMESPACE, "$name$DICTIONARY_SUFFIX").also { configMap ->
                expectThat(configMap) {
                    get { metadata }.and {
                        get { this.annotations } isEqualTo annotations
                    }
                    get { data }.isA<Map<String, String>>().and {
                        hasSize(1)
                        getValue("$name$DICTIONARY_SUFFIX") isEqualTo DICTIONARY_CONTENT
                    }
                }
            }
        }
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

        private fun HelmRelease.assertMinCfg(name: String, runAsJob: Boolean) {
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
                getValue(IS_JOB_ALIAS) isEqualTo runAsJob
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
