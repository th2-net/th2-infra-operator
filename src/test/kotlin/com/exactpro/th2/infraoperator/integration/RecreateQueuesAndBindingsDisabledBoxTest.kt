/*
 * Copyright 2024-2026 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQNamespacePermissions
import com.exactpro.th2.infraoperator.spec.Th2Spec
import com.exactpro.th2.infraoperator.spec.box.Th2Box
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.BindQueueLinkResolver
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.DeclareQueueResolver
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.tasks.RecreateQueuesAndBindings
import com.exactpro.th2.infraoperator.util.JsonUtils.YAML_MAPPER
import com.rabbitmq.http.client.Client
import io.fabric8.kubernetes.api.model.ObjectMeta
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.RabbitMQContainer
import java.nio.file.Path
import java.util.UUID

/**
 * Directly exercises [RecreateQueuesAndBindings] - the task the operator schedules when it detects
 * that its AMQP connection to RabbitMQ was lost (see `RabbitMQContext.RmqClientShutdownEventListener`)
 * - against a real RabbitMQ broker.
 *
 * This reproduces the reported bug: a box that was disabled (its queue correctly torn down) got its
 * queue silently recreated and bound after a RabbitMQ reconnect, because the recreation task iterated
 * every cached box resource without checking `spec.disabled`. Unlike [DisabledBoxQueueReconnectTest],
 * this test does not require a Kubernetes cluster or the operator's real 120s reconnect-retry delay -
 * it calls the exact same production code the reconnect handler calls, just without needing to wait for
 * an actual TCP-level connection drop to be detected.
 */
@Tag("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecreateQueuesAndBindingsDisabledBoxTest {

    private lateinit var rabbitMQContainer: RabbitMQContainer

    private lateinit var rabbitMQClient: Client

    private lateinit var rabbitMQContext: RabbitMQContext

    @BeforeAll
    @Timeout(30_000)
    fun beforeAll(@TempDir tempDir: Path) {
        rabbitMQContainer = createRabbitMQContainer()
        rabbitMQClient = createRabbitMQClient(rabbitMQContainer)

        val operatorConfig = createOperatorConfig(
            rabbitMQContainer,
            setOf(TH2_PREFIX),
            RABBIT_MQ_V_HOST,
            RABBIT_MQ_TOPIC_EXCHANGE,
            RabbitMQNamespacePermissions(),
        )
        val operatorCfgFile = tempDir.resolve("infra-operator.yml")
        YAML_MAPPER.writeValue(operatorCfgFile.toFile(), operatorConfig)
        System.setProperty(
            com.exactpro.th2.infraoperator.configuration.ConfigLoader.CONFIG_FILE_SYSTEM_PROPERTY,
            operatorCfgFile.toAbsolutePath().toString(),
        )

        rabbitMQContext = RabbitMQContext(operatorConfig.rabbitMQManagement)
        // namespace exchange that subscriber queues get bound to (RabbitMQContext.toExchangeName is
        // the identity function, so the exchange is literally named after the namespace)
        rabbitMQContext.channel.exchangeDeclare(TH2_NAMESPACE, RabbitMQContext.DIRECT, true)
    }

    @AfterAll
    @Timeout(30_000)
    fun afterAll() {
        if (this::rabbitMQContext.isInitialized) {
            rabbitMQContext.close()
        }
        if (this::rabbitMQContainer.isInitialized) {
            rabbitMQContainer.stop()
        }
    }

    @Test
    @Timeout(30_000)
    fun `reconnect recovery does not recreate a disabled box's queue`() {
        val declareQueueResolver = DeclareQueueResolver(rabbitMQContext)
        val bindQueueLinkResolver = BindQueueLinkResolver(rabbitMQContext)

        val pubName = "test-publisher"
        val subName = "test-subscriber"

        val pubResource = th2Box(
            pubName,
            """
            imageName: $IMAGE
            imageVersion: $VERSION
            type: th2-codec
            pins:
              mq:
                publishers:
                - name: $PUBLISH_PIN
                  attributes: [publish]
            """.trimIndent(),
        )
        val subResource = th2Box(
            subName,
            """
            imageName: $IMAGE
            imageVersion: $VERSION
            type: th2-codec
            pins:
              mq:
                subscribers:
                - name: $SUBSCRIBE_PIN
                  attributes: [subscribe]
                  linkTo:
                  - box: $pubName
                    pin: $PUBLISH_PIN
            """.trimIndent(),
        )

        // initial rollout: the operator declares + binds the subscriber's queue
        declareQueueResolver.resolveAdd(subResource)
        bindQueueLinkResolver.resolveDeclaredLinks(subResource)
        bindQueueLinkResolver.resolveHiddenLinks(subResource)

        val queueName = formatQueue(TH2_NAMESPACE, subName, SUBSCRIBE_PIN)
        val routingKey = formatRoutingKey(TH2_NAMESPACE, pubName, PUBLISH_PIN)
        rabbitMQClient.assertQueue(queueName, RABBIT_MQ_QUEUE_CLASSIC_TYPE, RABBIT_MQ_V_HOST)
        rabbitMQClient.assertBindings(queueName, RABBIT_MQ_V_HOST, setOf(routingKey))

        // user disables the subscriber box: the operator tears its queue down immediately
        // (mirrors HelmReleaseTh2Op.deletedEvent -> DeclareQueueResolver.resolveDelete)
        val disabledSubResource = th2Box(
            subName,
            """
            imageName: $IMAGE
            imageVersion: $VERSION
            type: th2-codec
            disabled: true
            pins:
              mq:
                subscribers:
                - name: $SUBSCRIBE_PIN
                  attributes: [subscribe]
                  linkTo:
                  - box: $pubName
                    pin: $PUBLISH_PIN
            """.trimIndent(),
        )
        declareQueueResolver.resolveDelete(disabledSubResource)
        rabbitMQClient.assertNoQueue(queueName, RABBIT_MQ_V_HOST)

        // RabbitMQ connection is lost and reconnected: the operator recreates queues/bindings for
        // every box resource still in its cache - including the disabled one, since disabling a box
        // only removes its HelmRelease, not the cached Th2CustomResource (see OperatorState)
        RecreateQueuesAndBindings(
            rabbitMQContext,
            listOf(pubResource, disabledSubResource),
            0,
        ).run()

        // the disabled box's queue must stay gone - it must not be silently recreated, unbound
        // and unconsumed, accumulating messages forever
        rabbitMQClient.assertNoQueue(queueName, RABBIT_MQ_V_HOST)
    }

    private fun th2Box(name: String, spec: String): Th2Box = Th2Box().apply {
        metadata = ObjectMeta().apply {
            this.name = name
            this.namespace = TH2_NAMESPACE
            this.uid = UUID.randomUUID().toString()
        }
        this.spec = YAML_MAPPER.readValue(spec, Th2Spec::class.java)
    }

    companion object {
        private const val TH2_PREFIX = "th2-"
        private const val TH2_NAMESPACE = "${TH2_PREFIX}reconnect-unit-test"

        private const val RABBIT_MQ_V_HOST = "/"
        private const val RABBIT_MQ_TOPIC_EXCHANGE = "test-global-exchange"

        private const val PUBLISH_PIN = "test-publish-pin"
        private const val SUBSCRIBE_PIN = "test-subscribe-pin"

        private const val IMAGE = "ghcr.io/th2-net/th2-codec"
        private const val VERSION = "0.0.0"
    }
}
