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
import com.exactpro.th2.infraoperator.model.box.mq.factory.MessageRouterConfigFactory
import com.exactpro.th2.infraoperator.model.box.mq.factory.MessageRouterConfigFactoryBox
import com.exactpro.th2.infraoperator.model.box.mq.factory.MessageRouterConfigFactoryEstore
import com.exactpro.th2.infraoperator.model.box.mq.factory.MessageRouterConfigFactoryMstore
import com.exactpro.th2.infraoperator.spec.Th2CustomResource
import com.exactpro.th2.infraoperator.spec.estore.Th2Estore
import com.exactpro.th2.infraoperator.spec.mstore.Th2Mstore
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext
import com.rabbitmq.http.client.domain.ExchangeInfo
import com.rabbitmq.http.client.domain.QueueInfo
import io.fabric8.kubernetes.client.KubernetesClient
import mu.KotlinLogging
import java.io.IOException

private val K_LOGGER = KotlinLogging.logger { }

fun deleteRabbitMQRubbish() {
    val config = ConfigLoader.loadConfiguration()
    val rabbitMQManagement = config.rabbitMQManagement

    if (!rabbitMQManagement.cleanUpOnStart) {
        K_LOGGER.info { "Cleanup RabbitMQ before start is skipped by config" }
        return
    }

    val namespacePrefixes = config.namespacePrefixes
    val topicExchange = rabbitMQManagement.exchangeName

    createKubernetesClient().use { kuClient ->
        val namespaces = kuClient.namespaces(namespacePrefixes)
        val resourceHolder = kuClient.collectRabbitMQRubbish(namespaces, topicExchange)
        K_LOGGER.info { "RabbitMQ rubbish: $resourceHolder" }
        deleteRabbitMQRubbish(resourceHolder)
    }
}

private fun KubernetesClient.collectRabbitMQRubbish(
    namespaces: Set<String>,
    topicExchange: String,
) = ResourceHolder().apply {
    val factories = createFactories()

    RabbitMQContext
        .getTh2Queues()
        .asSequence()
        .map(QueueInfo::getName)
        .forEach(queues::add)

    RabbitMQContext
        .getTh2Exchanges()
        .asSequence()
        .map(ExchangeInfo::getName)
        .forEach(exchanges::add)

    K_LOGGER.debug { "Actual set in RabbitMQ, queues: $queues, exchanges: $exchanges" }

    if (isHolderEmpty() || namespaces.isEmpty()) {
        return@apply
    }

    K_LOGGER.debug { "Search RabbitMQ resources in $namespaces namespaces" }
    exchanges.remove(topicExchange)

    namespaces.forEach { namespace ->
        customResources(namespace).forEach { cr ->
            val configuration = factories[cr.javaClass]?.createConfig(cr)
                ?: error("MQ config factory isn't present for ${cr.javaClass.simpleName}")

            configuration.queues.values.forEach { queueCfg ->
                if (queueCfg.queueName.isNotBlank()) {
                    queues.remove(queueCfg.queueName)
                }
                exchanges.remove(queueCfg.exchange)
            }
        }

        K_LOGGER.debug {
            "Survived RabbitMQ resources after '$namespace' namespace process, " +
                "queues: $queues, exchanges: $exchanges"
        }
    }
}

private fun deleteRabbitMQRubbish(resourceHolder: ResourceHolder) {
    if (resourceHolder.isHolderEmpty()) {
        return
    }

    val channel = RabbitMQContext.getChannel()

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

private data class ResourceHolder(
    val queues: MutableSet<String> = hashSetOf(),
    val exchanges: MutableSet<String> = hashSetOf(),
) {
    fun isHolderEmpty() = queues.isEmpty() and exchanges.isEmpty()

    override fun toString(): String = "queues=$queues, exchanges=$exchanges"
}
