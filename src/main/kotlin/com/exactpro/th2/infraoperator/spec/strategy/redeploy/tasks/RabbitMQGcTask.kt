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
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.buildEstoreQueue
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.buildMstoreQueue
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext
import com.exactpro.th2.infraoperator.util.ExtractUtils
import com.exactpro.th2.infraoperator.util.HelmReleaseUtils
import com.rabbitmq.http.client.domain.QueueInfo
import mu.KotlinLogging
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RabbitMQGcTask(
    private val retryDelay: Long
) : Task {
    private val lock = ReentrantLock()
    private var deleteCandidates: Set<String> = emptySet()

    override fun run(): Unit = lock.withLock {
        deleteRedundantQueues()
    }

    override fun getName(): String = "RabbitMQGC"

    override fun getRetryDelay(): Long = retryDelay

    private fun deleteRedundantQueues() {
        try {
            val potentialGarbage = hashSetOf<String>()
            RabbitMQContext.getTh2Queues().asSequence()
                .map(QueueInfo::getName)
                .forEach(potentialGarbage::add)

            if (potentialGarbage.isEmpty()) {
                deleteCandidates = emptySet()
                return
            }

            val operatorState = OperatorState.INSTANCE
            operatorState.namespaces.forEach { namespace ->
                potentialGarbage.remove(buildEstoreQueue(namespace))
                potentialGarbage.remove(buildMstoreQueue(namespace))
            }

            operatorState.getAllBoxResources().asSequence()
                .flatMap { cr ->
                    val namespace = ExtractUtils.extractNamespace(cr)
                    val name = ExtractUtils.extractName(cr)
                    val hr = operatorState.getHelmReleaseFromCache(name, namespace)
                    sequence<String> {
                        yieldAll(HelmReleaseUtils.extractQueues(hr.componentValuesSection))
                        yield(buildEstoreQueue(namespace, name))
                    }
                }.forEach(potentialGarbage::remove)

            if (potentialGarbage.isEmpty()) {
                deleteCandidates = emptySet()
                return
            }

            val channel = RabbitMQContext.getChannel()
            val nextDeleteCandidates = potentialGarbage.minus(deleteCandidates).toMutableSet()
            deleteCandidates.intersect(potentialGarbage).forEach { queue ->
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
                deleteCandidates = nextDeleteCandidates
            } else {
                deleteCandidates = emptySet()
            }
        } catch (e: IOException) {
            K_LOGGER.error(e) { "$name task failure" }
        }
    }

    companion object {
        private val K_LOGGER = KotlinLogging.logger { }
    }
}
