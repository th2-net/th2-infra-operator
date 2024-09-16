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

import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQNamespacePermissions
import com.rabbitmq.http.client.Client
import com.rabbitmq.http.client.domain.DestinationType
import com.rabbitmq.http.client.domain.QueueInfo
import org.junit.jupiter.api.assertAll
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

const val RABBIT_MQ_QUEUE_CLASSIC_TYPE = "classic"

fun Client.assertUser(
    user: String,
    vHost: String,
    permissions: RabbitMQNamespacePermissions,
    timeout: Long = 5_000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    await("assertUser('$user')")
        .timeout(timeout, unit)
        .until { users.firstOrNull { it.name == user } != null }

    val userInfo = users.firstOrNull { it.name == user }
    assertNotNull(userInfo, "User '$user' isn't found")
    assertEquals(emptyList(), userInfo.tags, "User '$user' has tags")
    val userPermissions = this.permissions.firstOrNull { it.user == user }
    assertNotNull(userPermissions, "User permission '$user' isn't found")
    assertEquals(vHost, userPermissions.vhost, "User permission '$user' has incorrect vHost")
    assertEquals(
        permissions.configure,
        userPermissions.configure,
        "User permission '$user' has incorrect configure permission",
    )
    assertEquals(permissions.read, userPermissions.read, "User permission '$user' has incorrect read permission")
    assertEquals(permissions.write, userPermissions.write, "User permission '$user' has incorrect write permission")
}

fun Client.assertNoUser(
    user: String,
    timeout: Long = 5_000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    await("assertNoUser('$user')")
        .timeout(timeout, unit)
        .until { users.firstOrNull { it.name == user } == null }
}

fun Client.assertExchange(
    exchange: String,
    type: String,
    vHost: String,
    timeout: Long = 5_000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    await("assertExchange('$exchange')")
        .timeout(timeout, unit)
        .until { exchanges.firstOrNull { it.name == exchange } != null }

    val exchangeInfo = exchanges.firstOrNull { it.name == exchange }
    assertNotNull(exchangeInfo, "Exchange '$exchange' isn't found")
    assertEquals(type, exchangeInfo.type, "Exchange '$exchange' has incorrect type")
    assertEquals(vHost, exchangeInfo.vhost, "Exchange '$exchange' has incorrect vHost")
    assertEquals(emptyMap(), exchangeInfo.arguments, "Exchange '$exchange' has arguments")
    assertTrue(exchangeInfo.isDurable, "Exchange '$exchange' isn't durable")
    assertFalse(exchangeInfo.isInternal, "Exchange '$exchange' is internal")
    assertFalse(exchangeInfo.isAutoDelete, "Exchange '$exchange' is auto delete")
}

fun Client.assertNoExchange(
    exchange: String,
    timeout: Long = 5_000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    await("assertNoExchange('$exchange')")
        .timeout(timeout, unit)
        .until { exchanges.firstOrNull { it.name == exchange } == null }
}

fun Client.assertQueue(
    queue: String,
    type: String,
    vHost: String,
    timeout: Long = 5_000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
): QueueInfo {
    await("assertQueue('$queue'")
        .timeout(timeout, unit)
        .until { getQueue(vHost, queue) != null }

    return assertNotNull(getQueue(vHost, queue), "Queue '$queue' isn't found")
        .also { queueInfo ->
            assertEquals(type, queueInfo.type, "Queue '$queue' has incorrect type")
            assertEquals(emptyMap(), queueInfo.arguments, "Queue '$queue' has arguments")
            assertTrue(queueInfo.isDurable, "Queue '$queue' isn't durable")
            assertFalse(queueInfo.isExclusive, "Queue '$queue' is exclusive")
            assertFalse(queueInfo.isAutoDelete, "Queue '$queue' is auto delete")
        }
}

fun Client.awaitQueueSize(
    queue: String,
    vHost: String,
    size: Long,
    timeout: Long = 5_000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    await("awaitQueueSize('$queue', size: $size")
        .timeout(timeout, unit)
        .until { getQueue(vHost, queue)?.let { it.messagesReady == size } }
}

fun Client.assertBindings(
    queue: String,
    vHost: String,
    routingKeys: Set<String> = emptySet(),
    timeout: Long = 5_000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    await("assertBindings('$queue'), routing keys: $routingKeys")
        .timeout(timeout, unit)
        .until { getQueueBindings(vHost, queue).size == routingKeys.size }

    val queueBindings = getQueueBindings(vHost, queue)
    assertAll(routingKeys.map { routingKey ->
            {
                val queueBinding =
                    assertNotNull(
                        queueBindings.singleOrNull { it.routingKey == routingKey },
                        "Queue '$queue' doesn't contain routing key, actual: $queueBindings",
                    )
                assertAll(
                    {
                        assertEquals(
                            vHost,
                            queueBinding.vhost,
                            "Binding has incorrect vHost for routing key '$routingKey' in queue '$queue'",
                        )
                    },
                    {
                        assertEquals(
                            emptyMap(),
                            queueBinding.arguments,
                            "Binding has arguments for routing key '$routingKey' in queue '$queue'",
                        )
                    },
                    {
                        assertEquals(
                            routingKey.replace("[", "%5B").replace(":", "%3A").replace("]", "%5D"),
                            queueBinding.propertiesKey,
                            "Binding has 'propertiesKey' for routing key '$routingKey' in queue '$queue'",
                        )
                    },
                    {
                        assertEquals(
                            queue,
                            queueBinding.destination,
                            "Binding has incorrect 'destination' for routing key '$routingKey' in queue '$queue'",
                        )
                    },
                    {
                        assertEquals(
                            DestinationType.QUEUE,
                            queueBinding.destinationType,
                            "Binding has incorrect 'destinationType' for routing key '$routingKey' in queue '$queue'",
                        )
                    },
                )
            }
        },
    )
}

fun Client.assertNoQueues(
    queuePattern: String,
    vHost: String,
    timeout: Long = 5_000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    await("assertNoQueues('$queuePattern')")
        .timeout(timeout, unit)
        .until { queues.map { it.name.matches(Regex(queuePattern)) && it.vhost == vHost }.isEmpty() }
}

fun Client.assertNoQueue(
    name: String,
    vHost: String,
    timeout: Long = 5_000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    await("assertNoQueue('$name')")
        .timeout(timeout, unit)
        .until { getQueue(vHost, name) == null }
}