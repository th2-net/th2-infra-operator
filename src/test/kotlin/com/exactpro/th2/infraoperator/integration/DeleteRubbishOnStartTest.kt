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
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQNamespacePermissions
import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.EVENT_STORAGE_BOX_ALIAS
import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS
import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.MESSAGE_STORAGE_BOX_ALIAS
import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.MESSAGE_STORAGE_PIN_ALIAS
import com.exactpro.th2.infraoperator.spec.box.Th2Box
import com.exactpro.th2.infraoperator.spec.shared.status.RolloutPhase
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext.DIRECT
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext.toExchangeName
import com.exactpro.th2.infraoperator.util.createKubernetesClient
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Connection
import com.rabbitmq.http.client.Client
import io.fabric8.kubernetes.client.KubernetesClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.k3s.K3sContainer
import java.nio.file.Path
import kotlin.test.Test

@Tag("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeleteRubbishOnStartTest {

    private lateinit var k3sContainer: K3sContainer

    private lateinit var rabbitMQContainer: RabbitMQContainer

    private lateinit var kubeClient: KubernetesClient

    private lateinit var rabbitMQClient: Client

    private lateinit var rabbitMQConnection: Connection

    @BeforeAll
    @Timeout(30_000)
    fun beforeAll(@TempDir tempDir: Path) {
        k3sContainer = createK3sContainer()
        rabbitMQContainer = createRabbitMQContainer()

        prepareTh2CfgDir(
            k3sContainer.kubeConfigYaml,
            createOperatorConfig(
                rabbitMQContainer,
                setOf(TH2_PREFIX),
                RABBIT_MQ_V_HOST,
                RABBIT_MQ_TOPIC_EXCHANGE,
                RABBIT_MQ_NAMESPACE_PERMISSIONS,
            ),
            tempDir,
        )

        kubeClient = createKubernetesClient().apply { configureK3s() }
        rabbitMQClient = createRabbitMQClient(rabbitMQContainer)
        rabbitMQConnection = createRabbitMQConnection(rabbitMQContainer, RABBIT_MQ_V_HOST)
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
        if (this::rabbitMQConnection.isInitialized && rabbitMQConnection.isOpen) {
            rabbitMQConnection.close()
        }
    }

    @Test
    @Timeout(30_000)
    fun deleteAllTest() {
        val namespaces = listOf(
            "${TH2_PREFIX}test-b",
            "${TH2_PREFIX}test-c"
        )
        val component = "test-component"

        rabbitMQConnection.createChannel().use { channel ->
            val queues = mutableListOf<String>()
            val exchanges = mutableListOf<String>()

            namespaces.forEach { namespace ->
                channel.createQueue(namespace, "rubbish-component", PIN_NAME)
                    .assertQueue().queue.also(queues::add)
                channel.createQueue(namespace, component, "rubbish-pin")
                    .assertQueue().queue.also(queues::add)
                channel.createQueue(namespace, component, PIN_NAME)
                    .assertQueue().queue.also(queues::add)
                channel.createQueue(namespace, MESSAGE_STORAGE_BOX_ALIAS, MESSAGE_STORAGE_PIN_ALIAS)
                    .assertQueue().queue.also(queues::add)
                channel.createQueue(namespace, EVENT_STORAGE_BOX_ALIAS, EVENT_STORAGE_PIN_ALIAS)
                    .assertQueue().queue.also(queues::add)

                toExchangeName(namespace).apply {
                    channel.createExchange(this, DIRECT)
                    rabbitMQClient.assertExchange(this, DIRECT, RABBIT_MQ_V_HOST)
                    exchanges.add(this)
                }
            }

            Th2CrdController().use {
                assertAll(
                    queues.map { queue ->
                        {
                            rabbitMQClient.assertNoQueue(queue, RABBIT_MQ_V_HOST)
                        }
                    } + exchanges.map { exchange ->
                        {
                            rabbitMQClient.assertNoExchange(exchange, RABBIT_MQ_V_HOST)
                        }
                    } + listOf(
                        { rabbitMQClient.assertNoQueues("link\\[.*\\]", RABBIT_MQ_V_HOST) },
                        { rabbitMQClient.assertNoExchanges("${TH2_PREFIX}.*", RABBIT_MQ_V_HOST) }
                    )
                )
            }
        }
    }

    @Test
    fun deleteRubbishTest() {
        var gitHash = RESOURCE_GIT_HASH_COUNTER.incrementAndGet().toString()

        val namespaceB = "${TH2_PREFIX}test-b"
        val namespaceC = "${TH2_PREFIX}test-c"

        val exchangeB = toExchangeName(namespaceB)
        val exchangeC = toExchangeName(namespaceC)

        val component = "test-component"

        prepareNamespace(gitHash, namespaceB)

        rabbitMQConnection.createChannel().use { channel ->
            channel.confirmSelect()
            /** queue of not existed component */
            val queue01 = channel.createQueue(namespaceB, "rubbish-component", PIN_NAME).assertQueue()

            /** queue of not exited pin */
            val queue02 = channel.createQueue(namespaceB, component, "rubbish-pin").assertQueue()

            /** mstore queue of not existed namespace */
            val queue03 = channel.createQueue(
                namespaceC,
                MESSAGE_STORAGE_BOX_ALIAS,
                MESSAGE_STORAGE_PIN_ALIAS
            ).assertQueue()

            /** mstore queue of existed namespace */
            val queue11 = channel.createQueue(
                namespaceB,
                MESSAGE_STORAGE_BOX_ALIAS,
                MESSAGE_STORAGE_PIN_ALIAS
            ).assertQueue()
                .also {
                    channel.basicPublish("", it.queue, null, "test-content".toByteArray())
                }

            /** queue of exited component and pin */
            val queue12 = channel.createQueue(namespaceB, component, PIN_NAME).assertQueue()
                .also {
                    channel.basicPublish("", it.queue, null, "test-content".toByteArray())
                }

            /** exchange of not exited namespace */
            channel.createExchange(exchangeC, DIRECT)
            rabbitMQClient.assertExchange(exchangeC, DIRECT, RABBIT_MQ_V_HOST)

            // TODO: add routing keys check:
            //  * existed key to exited queue
            //  * existed exchange A to existed queue B
            //  * not existed key to exited queue

            gitHash = RESOURCE_GIT_HASH_COUNTER.incrementAndGet().toString()
            val spec = """
                imageName: "ghcr.io/th2-net/th2-component"
                imageVersion: "0.0.0"
                type: th2-codec
                pins:
                  mq:
                    subscribers:
                    - name: $PIN_NAME
                      attributes: [subscribe]
            """.trimIndent()
            kubeClient.createTh2CustomResource(exchangeB, component, gitHash, spec, ::Th2Box)

            Th2CrdController().use {
                kubeClient.awaitPhase(exchangeB, component, RolloutPhase.SUCCEEDED, Th2Box::class.java)

                rabbitMQClient.assertNoQueue(queue01.queue, RABBIT_MQ_V_HOST)
                rabbitMQClient.assertNoQueue(queue02.queue, RABBIT_MQ_V_HOST)
                rabbitMQClient.assertNoQueue(queue03.queue, RABBIT_MQ_V_HOST)

                rabbitMQClient.assertQueue(queue11.queue, RABBIT_MQ_QUEUE_CLASSIC_TYPE, RABBIT_MQ_V_HOST)
                rabbitMQClient.awaitQueueSize(queue11.queue, RABBIT_MQ_V_HOST, 1)
                rabbitMQClient.assertQueue(queue12.queue, RABBIT_MQ_QUEUE_CLASSIC_TYPE, RABBIT_MQ_V_HOST)
                rabbitMQClient.awaitQueueSize(queue12.queue, RABBIT_MQ_V_HOST, 1)

                rabbitMQClient.assertNoExchange(exchangeC, RABBIT_MQ_V_HOST)
            }
        }
    }

    private fun prepareNamespace(gitHash: String, namespace: String) {
        kubeClient.createNamespace(namespace)
        kubeClient.createRabbitMQSecret(namespace, gitHash)
        kubeClient.createRabbitMQAppConfigCfgMap(
            namespace,
            gitHash,
            createRabbitMQConfig(rabbitMQContainer, RABBIT_MQ_V_HOST, toExchangeName(namespace), namespace)
        )

        kubeClient.createBookConfigCfgMap(namespace, gitHash, TH2_BOOK)
        kubeClient.createLoggingCfgMap(namespace, gitHash)
        kubeClient.createMQRouterCfgMap(namespace, gitHash)
        kubeClient.createGrpcRouterCfgMap(namespace, gitHash)
        kubeClient.createCradleManagerCfgMap(namespace, gitHash)
    }

    private fun AMQP.Queue.DeclareOk.assertQueue(): AMQP.Queue.DeclareOk {
        rabbitMQClient.assertQueue(queue, RABBIT_MQ_QUEUE_CLASSIC_TYPE, RABBIT_MQ_V_HOST)
        return this
    }

    companion object {
        private const val TH2_PREFIX = "th2-"
        private const val TH2_BOOK = "test_book"

        private val RABBIT_MQ_NAMESPACE_PERMISSIONS = RabbitMQNamespacePermissions()
        private const val RABBIT_MQ_V_HOST = "/"
        private const val RABBIT_MQ_TOPIC_EXCHANGE = "test-global-exchange"

        private const val PIN_NAME = "test-pin"
    }
}
