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

import com.exactpro.th2.infraoperator.configuration.ConfigLoader
import com.exactpro.th2.infraoperator.configuration.OperatorConfig
import com.exactpro.th2.infraoperator.configuration.fields.ChartSpec
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQConfig
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQManagementConfig
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQNamespacePermissions
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.BOOK_CONFIG_CM_NAME
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.DEFAULT_BOOK
import com.exactpro.th2.infraoperator.spec.shared.PrometheusConfiguration
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext
import com.exactpro.th2.infraoperator.util.JsonUtils
import com.fasterxml.jackson.module.kotlin.readValue
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.http.client.Client
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.LoggerFactory
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.k3s.K3sContainer
import org.testcontainers.lifecycle.Startable
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

private val K3S_DOCKER_IMAGE = DockerImageName.parse("rancher/k3s:v1.21.3-k3s1")
private val RABBITMQ_DOCKER_IMAGE = DockerImageName.parse("rabbitmq:3.12.6-management")

private val K_LOGGER = KotlinLogging.logger {}

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

val RESOURCE_GIT_HASH_COUNTER = AtomicLong(10_000_000)

fun KubernetesClient.configureK3s() {
    CRD_RESOURCE_NAMES
        .asSequence()
        .map(::loadCrd)
        .map(this::resource)
        .forEach { crd ->
            crd.create()
            K_LOGGER.info { "Applied CRD: ${crd.get().metadata.name}" }
        }
}

fun KubernetesClient.createRabbitMQSecret(
    namespace: String,
    gitHash: String,
) {
    val data = mapOf(OperatorConfig.RABBITMQ_SECRET_PASSWORD_KEY to UUID.randomUUID().toString())
    createSecret(
        namespace,
        "rabbitmq",
        createAnnotations(gitHash, data),
        data,
    )
}

fun KubernetesClient.createRabbitMQAppConfigCfgMap(
    namespace: String,
    gitHash: String,
    data: RabbitMQConfig,
) {
    val content = JsonUtils.JSON_MAPPER.writeValueAsString(data)
    createConfigMap(
        namespace,
        OperatorConfig.DEFAULT_RABBITMQ_CONFIGMAP_NAME,
        createAnnotations(gitHash, content),
        mapOf(RabbitMQConfig.CONFIG_MAP_RABBITMQ_PROP_NAME to content),
    )
}

fun KubernetesClient.createBookConfigCfgMap(
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

fun KubernetesClient.createLoggingCfgMap(
    namespace: String,
    gitHash: String,
) {
    val data = mapOf("log4j2.properties" to "")
    createConfigMap(
        namespace,
        ConfigMapEventHandler.LOGGING_CM_NAME,
        createAnnotations(gitHash, data),
        data,
    )
}

fun KubernetesClient.createMQRouterCfgMap(
    namespace: String,
    gitHash: String,
) {
    val data = mapOf("mq_router.json" to "{}")
    createConfigMap(
        namespace,
        ConfigMapEventHandler.MQ_ROUTER_CM_NAME,
        createAnnotations(gitHash, data),
        data,
    )
}

fun KubernetesClient.createGrpcRouterCfgMap(
    namespace: String,
    gitHash: String,
) {
    val data = mapOf("grpc_router.json" to "{}")
    createConfigMap(
        namespace,
        ConfigMapEventHandler.GRPC_ROUTER_CM_NAME,
        createAnnotations(gitHash, data),
        data,
    )
}

fun KubernetesClient.createCradleManagerCfgMap(
    namespace: String,
    gitHash: String,
) {
    val data = mapOf("cradle_manager.json" to "{}")
    createConfigMap(
        namespace,
        ConfigMapEventHandler.CRADLE_MANAGER_CM_NAME,
        createAnnotations(gitHash, data),
        data,
    )
}

fun createK3sContainer(): K3sContainer = K3sContainer(K3S_DOCKER_IMAGE)
    .withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("K3S")).withSeparateOutputStreams())
    .also(Startable::start)

fun createRabbitMQContainer(): RabbitMQContainer = RabbitMQContainer(RABBITMQ_DOCKER_IMAGE)
    .withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("RABBIT_MQ")).withSeparateOutputStreams())
    .also(Startable::start)

fun createRabbitMQClient(rabbitMQ: RabbitMQContainer): Client =
    RabbitMQContext.createClient(
        rabbitMQ.host,
        rabbitMQ.httpPort,
        rabbitMQ.adminUsername,
        rabbitMQ.adminPassword,
    )

fun createRabbitMQConnection(rabbitMQ: RabbitMQContainer, vHost: String): Connection =
    ConnectionFactory().apply {
        host = rabbitMQ.host
        port = rabbitMQ.amqpPort
        virtualHost = vHost
        username = rabbitMQ.adminUsername
        password = rabbitMQ.adminPassword
    }.newConnection("integration-test")

fun createRabbitMQConfig(
    rabbitMQ: RabbitMQContainer,
    vHost: String,
    exchange: String,
    user: String,
) = RabbitMQConfig(
    rabbitMQ.amqpPort,
    rabbitMQ.host,
    vHost,
    exchange,
    user,
    "${'$'}{RABBITMQ_PASS}",
)

fun formatQueue(namespace: String, component: String, pin: String) = "link[$namespace:$component:$pin]"

fun formatRoutingKey(namespace: String, component: String, pin: String) = "key[$namespace:$component:$pin]"

fun Channel.createQueue(
    namespace: String,
    component: String,
    pin: String,
    durable: Boolean = true,
    exclusive: Boolean = false,
    autoDelete: Boolean = false,
    arguments: Map<String, Any> = emptyMap(),
): AMQP.Queue.DeclareOk = queueDeclare(
    formatQueue(namespace, component, pin),
    durable,
    exclusive,
    autoDelete,
    arguments
)

fun Channel.createExchange(
    exchange: String,
    type: String,
    durable: Boolean = true,
    exclusive: Boolean = false,
    autoDelete: Boolean = false,
    arguments: Map<String, Any> = emptyMap(),
): AMQP.Exchange.DeclareOk = exchangeDeclare(exchange, type, durable, exclusive, autoDelete, arguments)

fun prepareTh2CfgDir(
    kubeConfigYaml: String,
    operatorConfig: OperatorConfig,
    baseDir: Path
) {
    val configDir = baseDir.resolve("cfg")
    val kubeCfgFile = configDir.resolve("kube-config.yaml")
    val operatorCfgFile = configDir.resolve("infra-operator.yml")
    configDir.createDirectories()

    Files.writeString(kubeCfgFile, kubeConfigYaml)
    JsonUtils.YAML_MAPPER.writeValue(operatorCfgFile.toFile(), operatorConfig)

    System.setProperty(Config.KUBERNETES_KUBECONFIG_FILE, kubeCfgFile.absolutePathString())
    System.setProperty(ConfigLoader.CONFIG_FILE_SYSTEM_PROPERTY, operatorCfgFile.absolutePathString())
}

fun createOperatorConfig(
    rabbitMQ: RabbitMQContainer,
    namespacePrefixes: Set<String>,
    vHost: String,
    topicExchange: String,
    permissions: RabbitMQNamespacePermissions,
) =
    OperatorConfig(
        chart = ChartSpec(),
        namespacePrefixes = namespacePrefixes,
        rabbitMQManagement =
        RabbitMQManagementConfig(
            host = rabbitMQ.host,
            managementPort = rabbitMQ.httpPort,
            applicationPort = rabbitMQ.amqpPort,
            vhostName = vHost,
            exchangeName = topicExchange,
            username = rabbitMQ.adminUsername,
            password = rabbitMQ.adminPassword,
            persistence = true,
            schemaPermissions = permissions,
        ),
        prometheusConfiguration =
        PrometheusConfiguration(
            "0.0.0.0",
            "9752",
            false.toString(),
        ),
    )

private fun loadCrd(resourceName: String): CustomResourceDefinition =
    requireNotNull(IntegrationTest::class.java.classLoader.getResource("crds/$resourceName")) {
        "Resource '$resourceName' isn't found"
    }.let(JsonUtils.YAML_MAPPER::readValue)
