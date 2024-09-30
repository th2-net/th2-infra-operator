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

@file:JvmName("RabbitMQUtils")

package com.exactpro.th2.infraoperator.util

import com.exactpro.th2.infraoperator.configuration.ConfigLoader
import com.exactpro.th2.infraoperator.model.box.mq.QueueConfiguration
import com.exactpro.th2.infraoperator.model.box.mq.factory.MessageRouterConfigFactory
import com.exactpro.th2.infraoperator.model.box.mq.factory.MessageRouterConfigFactoryBox
import com.exactpro.th2.infraoperator.model.box.mq.factory.MessageRouterConfigFactoryEstore
import com.exactpro.th2.infraoperator.model.box.mq.factory.MessageRouterConfigFactoryMstore
import com.exactpro.th2.infraoperator.spec.Th2CustomResource
import com.exactpro.th2.infraoperator.spec.estore.Th2Estore
import com.exactpro.th2.infraoperator.spec.mstore.Th2Mstore
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.createEstoreQueue
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.createMstoreQueue
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext
import com.rabbitmq.client.Channel
import com.rabbitmq.http.client.domain.ExchangeInfo
import com.rabbitmq.http.client.domain.QueueInfo
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException

private val K_LOGGER = KotlinLogging.logger { }

fun deleteRabbitMQRubbish(
    kubernetesClient: KubernetesClient,
    rabbitMQContext: RabbitMQContext,
) {
    try {
        val resourceHolder = collectRabbitMQResources(
            rabbitMQContext.th2Queues,
            rabbitMQContext.th2Exchanges,
        )

        if (resourceHolder.isHolderEmpty()) {
            return
        }

        val namespacePrefixes = ConfigLoader.loadConfiguration().namespacePrefixes
        val topicExchange = rabbitMQContext.topicExchangeName

        resourceHolder.filterRubbishResources(
            kubernetesClient,
            namespacePrefixes,
            topicExchange,
        )
        K_LOGGER.info { "RabbitMQ rubbish: $resourceHolder" }
        deleteRabbitMQRubbish(resourceHolder, rabbitMQContext::getChannel)
    } catch (e: Exception) {
        K_LOGGER.error(e) { "Delete RabbitMQ rubbish failure" }
    }
}

internal fun collectRabbitMQResources(
    th2Queues: Collection<QueueInfo>,
    th2Exchanges: Collection<ExchangeInfo>,
): ResourceHolder = ResourceHolder().apply {
    th2Queues.asSequence()
        .map(QueueInfo::getName)
        .forEach(queues::add)

    th2Exchanges.asSequence()
        .map(ExchangeInfo::getName)
        .forEach(exchanges::add)

    K_LOGGER.debug { "Actual set in RabbitMQ, queues: $queues, exchanges: $exchanges" }
}

internal fun ResourceHolder.filterRubbishResources(
    client: KubernetesClient,
    namespacePrefixes: Set<String>,
    topicExchange: String,
): ResourceHolder = apply {
    val namespaces: Set<String> = client.namespaces(namespacePrefixes)
    if (namespaces.isEmpty()) {
        return@apply
    }
    exchanges.remove(topicExchange) // FIXME: topic exchange should be declare after each namespace creation

    K_LOGGER.debug { "Search RabbitMQ resources in $namespaces namespaces" }

    val factories: Map<Class<out Th2CustomResource>, MessageRouterConfigFactory> = createFactories()
    namespaces.forEach { namespace ->
        queues.remove(createEstoreQueue(namespace))
        queues.remove(createMstoreQueue(namespace))
        exchanges.remove(namespace)

        client.customResources(namespace).asSequence()
            .flatMap { cr ->
                factories[cr.javaClass]?.createConfig(cr)?.queues?.values
                    ?: error("MQ config factory isn't present for ${cr.javaClass.simpleName}")
            }.map(QueueConfiguration::getQueueName)
            .filter(String::isNotBlank)
            .forEach(queues::remove)

        K_LOGGER.debug {
            "Survived RabbitMQ resources after '$namespace' namespace process, " +
                "queues: $queues, exchanges: $exchanges"
        }
    }
}

internal fun deleteRabbitMQRubbish(
    resourceHolder: ResourceHolder,
    getChannel: () -> Channel
) {
    if (resourceHolder.isHolderEmpty()) {
        return
    }

    val channel: Channel = getChannel()

    resourceHolder.queues.forEach { queue ->
        try {
            channel.queueDelete(queue)
            K_LOGGER.info { "Deleted '$queue' queue" }
        } catch (e: IOException) {
            K_LOGGER.error(e) { "'$queue' queue delete failure" }
        }
    }

    resourceHolder.exchanges.forEach { exchange ->
        try {
            channel.exchangeDelete(exchange)
            K_LOGGER.info { "Deleted '$exchange' exchange" }
        } catch (e: IOException) {
            K_LOGGER.error(e) { "'$exchange' queue delete failure" }
        }
    }
}

private fun createFactories(): Map<Class<out Th2CustomResource>, MessageRouterConfigFactory> {
    val defaultFactory = MessageRouterConfigFactoryBox()
    return CUSTOM_RESOURCE_KINDS
        .asSequence()
        .map {
            it to
                when (it) {
                    Th2Mstore::class.java -> MessageRouterConfigFactoryMstore()
                    Th2Estore::class.java -> MessageRouterConfigFactoryEstore()
                    else -> defaultFactory
                }
        }.toMap()
}

internal data class ResourceHolder(
    val queues: MutableSet<String> = hashSetOf(),
    val exchanges: MutableSet<String> = hashSetOf(),
) {
    fun isHolderEmpty() = queues.isEmpty() and exchanges.isEmpty()

    override fun toString(): String = "queues=$queues, exchanges=$exchanges"
}
