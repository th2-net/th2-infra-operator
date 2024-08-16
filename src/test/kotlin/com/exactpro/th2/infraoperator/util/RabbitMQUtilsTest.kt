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

package com.exactpro.th2.infraoperator.util

import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext.toExchangeName
import com.rabbitmq.client.Channel
import com.rabbitmq.http.client.domain.ExchangeInfo
import com.rabbitmq.http.client.domain.QueueInfo
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.NamespaceList
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.MixedOperation
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation
import io.fabric8.kubernetes.client.dsl.Resource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import com.exactpro.th2.infraoperator.spec.Th2CustomResource as CR

private const val TOPIC_EXCHANGE_NAME = "test-global-exchange"

class RabbitMQUtilsTest {
    @Test
    fun `no ns and topic exchange`() {
        val client: KubernetesClient = mockKubernetesClient()
        val actual =
            client.collectRabbitMQRubbish(
                setOf("th2"),
                TOPIC_EXCHANGE_NAME,
                emptyList(),
                listOf(
                    ExchangeInfo().apply { name = TOPIC_EXCHANGE_NAME },
                ),
            )
        val expected =
            ResourceHolder(
                exchanges = hashSetOf(TOPIC_EXCHANGE_NAME),
            )

        assertEquals(expected, actual)
    }

    @Test
    fun `no ns and rubbish exchange`() {
        val exchangeName = "th2-test-exchange"
        val client: KubernetesClient = mockKubernetesClient()
        val actual =
            client.collectRabbitMQRubbish(
                setOf("th2"),
                TOPIC_EXCHANGE_NAME,
                emptyList(),
                listOf(
                    ExchangeInfo().apply { name = exchangeName },
                ),
            )
        val expected =
            ResourceHolder(
                exchanges = hashSetOf(exchangeName),
            )

        assertEquals(expected, actual)
    }

    @Test
    fun `no ns but rubbish queue`() {
        val queueName = "test-link[th2-test-namespace:test-component:test-pin]"
        val client: KubernetesClient = mockKubernetesClient()
        val actual =
            client.collectRabbitMQRubbish(
                setOf("th2"),
                TOPIC_EXCHANGE_NAME,
                listOf(
                    QueueInfo().apply { name = queueName },
                ),
                emptyList(),
            )
        val expected =
            ResourceHolder(
                queues = hashSetOf(queueName),
            )

        assertEquals(expected, actual)
    }

    @Test
    fun `one ns and rubbish exchange`() {
        val exchangeName = "th2-test-exchange"
        val namespaceName = "th2-test-active-namespace"
        val client: KubernetesClient =
            mockKubernetesClient(
                setOf(namespaceName),
            )
        val actual =
            client.collectRabbitMQRubbish(
                setOf("th2"),
                TOPIC_EXCHANGE_NAME,
                emptyList(),
                listOf(
                    ExchangeInfo().apply { name = exchangeName },
                    ExchangeInfo().apply { name = toExchangeName(namespaceName) },
                    ExchangeInfo().apply { name = TOPIC_EXCHANGE_NAME },
                ),
            )
        val expected =
            ResourceHolder(
                exchanges = hashSetOf(exchangeName),
            )

        assertEquals(expected, actual)
    }

    @Test
    fun `one ns and rubbish queue`() {
        val queueName = "test-link[th2-test-namespace:test-component:test-pin]"
        val namespaceName = "th2-test-active-namespace"
        val client: KubernetesClient =
            mockKubernetesClient(
                setOf(namespaceName),
            )
        val actual =
            client.collectRabbitMQRubbish(
                setOf("th2"),
                TOPIC_EXCHANGE_NAME,
                listOf(
                    QueueInfo().apply { name = queueName },
                ),
                listOf(
                    ExchangeInfo().apply { name = toExchangeName(namespaceName) },
                    ExchangeInfo().apply { name = TOPIC_EXCHANGE_NAME },
                ),
            )
        val expected =
            ResourceHolder(
                queues = hashSetOf(queueName),
            )

        assertEquals(expected, actual)
    }

    @Test
    fun `delete rubbish`() {
        val channel: Channel = mock {}
        val resourceHolder =
            ResourceHolder(
                hashSetOf("queueA", "queueB"),
                hashSetOf("exchangeA", "exchangeB"),
            )
        deleteRabbitMQRubbish(
            resourceHolder,
        ) { channel }

        resourceHolder.queues.forEach {
            verify(channel).queueDelete(it)
        }

        resourceHolder.exchanges.forEach {
            verify(channel).exchangeDelete(it)
        }
    }

    companion object {
        fun mockKubernetesClient(namespaceNames: Set<String> = emptySet()): KubernetesClient {
            val namespaceList =
                NamespaceList().apply {
                    items =
                        namespaceNames.map { namespaceName ->
                            Namespace().apply {
                                metadata =
                                    ObjectMeta().apply {
                                        name = namespaceName
                                    }
                            }
                        }
                }
            val namespaces: NonNamespaceOperation<Namespace, NamespaceList, Resource<Namespace>> =
                mock {
                    on { list() }.thenReturn(namespaceList)
                }

            val mixedOperation:
                MixedOperation<CR, KubernetesResourceList<CR>, Resource<CR>> =
                mock {
                    on { inNamespace(any()) }.thenReturn(it)
                    on { resources() }.thenAnswer { emptyList<CR>().stream() }
                }
            return mock {
                on { namespaces() }.thenReturn(namespaces)
                on { resources(any<Class<CR>>()) }.thenReturn(mixedOperation)
            }
        }
    }
}
