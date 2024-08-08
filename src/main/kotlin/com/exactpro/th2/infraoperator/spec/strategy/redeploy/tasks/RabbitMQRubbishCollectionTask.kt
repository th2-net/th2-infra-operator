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

package com.exactpro.th2.infraoperator.spec.strategy.redeploy.tasks

import com.exactpro.th2.infraoperator.OperatorState
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.createEstoreQueue
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.createMstoreQueue
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext
import com.exactpro.th2.infraoperator.util.ExtractUtils
import com.exactpro.th2.infraoperator.util.HelmReleaseUtils
import com.rabbitmq.http.client.domain.ExchangeInfo
import com.rabbitmq.http.client.domain.QueueInfo
import mu.KotlinLogging
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RabbitMQRubbishCollectionTask(
    private val retryDelay: Long
) : Task {
    private val lock = ReentrantLock()
    private var queuesForDelete: Set<String> = emptySet()
    private var exchangesForDelete: Set<String> = emptySet()

    override fun run(): Unit = lock.withLock {
        try {
            val operatorState = OperatorState.INSTANCE

            deleteRedundantQueues(operatorState)
            deleteRedundantExchanges(operatorState)
        } catch (e: Exception) {
            K_LOGGER.error(e) { "$name task failure" }
        }
    }

    override fun getName(): String = RabbitMQRubbishCollectionTask::class.java.simpleName

    override fun getRetryDelay(): Long = retryDelay

    private fun deleteRedundantQueues(operatorState: OperatorState) {
        val potentialGarbage = hashSetOf<String>()
        RabbitMQContext.getTh2Queues().asSequence()
            .map(QueueInfo::getName)
            .forEach(potentialGarbage::add)

        if (potentialGarbage.isEmpty()) {
            queuesForDelete = emptySet()
            return
        }

        operatorState.namespaces.forEach { namespace ->
            potentialGarbage.remove(createEstoreQueue(namespace))
            potentialGarbage.remove(createMstoreQueue(namespace))
        }

        operatorState.getAllBoxResources().asSequence()
            .flatMap { cr ->
                val namespace = ExtractUtils.extractNamespace(cr)
                val name = ExtractUtils.extractName(cr)
                val hr = operatorState.getHelmReleaseFromCache(name, namespace)
                sequence<String> {
                    yieldAll(HelmReleaseUtils.extractQueues(hr.componentValuesSection))
                    yield(createEstoreQueue(namespace, name))
                }
            }.forEach(potentialGarbage::remove)

        if (potentialGarbage.isEmpty()) {
            queuesForDelete = emptySet()
            return
        }

        val channel = RabbitMQContext.getChannel()
        val nextDeleteCandidates = potentialGarbage.minus(queuesForDelete).toMutableSet()
        queuesForDelete.intersect(potentialGarbage).forEach { queue ->
            try {
                channel.queueDelete(queue)
                K_LOGGER.info { "Deleted queue: [$queue]" }
            } catch (e: IOException) {
                nextDeleteCandidates.add(queue)
                K_LOGGER.error(e) { "Exception deleting queue: [$queue]" }
            }
        }

        if (nextDeleteCandidates.isNotEmpty()) {
            K_LOGGER.info { "Queues $nextDeleteCandidates are delete candidates for the next iteration" }
            queuesForDelete = nextDeleteCandidates
        } else {
            queuesForDelete = emptySet()
        }
    }

    private fun deleteRedundantExchanges(operatorState: OperatorState) {
        val potentialGarbage = hashSetOf<String>()
        RabbitMQContext.getTh2Exchanges().asSequence()
            .map(ExchangeInfo::getName)
            .forEach(potentialGarbage::add)

        potentialGarbage.remove(RabbitMQContext.getTopicExchangeName())

        if (potentialGarbage.isEmpty()) {
            exchangesForDelete = emptySet()
            return
        }
        operatorState.namespaces.forEach(potentialGarbage::remove)

        if (potentialGarbage.isEmpty()) {
            exchangesForDelete = emptySet()
            return
        }

        val channel = RabbitMQContext.getChannel()
        val nextDeleteCandidates = potentialGarbage.minus(exchangesForDelete).toMutableSet()
        exchangesForDelete.intersect(potentialGarbage).forEach { exchange ->
            try {
                channel.exchangeDelete(exchange)
                K_LOGGER.info { "Deleted exchange: [$exchange]" }
            } catch (e: IOException) {
                nextDeleteCandidates.add(exchange)
                K_LOGGER.error(e) { "Exception deleting exchange: [$exchange]" }
            }
        }

        if (nextDeleteCandidates.isNotEmpty()) {
            K_LOGGER.info { "Exchanges $nextDeleteCandidates are delete candidates for the next iteration" }
            exchangesForDelete = nextDeleteCandidates
        } else {
            exchangesForDelete = emptySet()
        }
    }

    companion object {
        private val K_LOGGER = KotlinLogging.logger { }
    }
}
