/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq

import com.exactpro.th2.infraoperator.model.LinkDescription
import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.EVENT_STORAGE_BOX_ALIAS
import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS
import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.MESSAGE_STORAGE_BOX_ALIAS
import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.MESSAGE_STORAGE_PIN_ALIAS
import com.exactpro.th2.infraoperator.spec.Th2CustomResource
import com.exactpro.th2.infraoperator.spec.shared.PinAttribute
import com.exactpro.th2.infraoperator.spec.shared.pin.Link
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.QueueName
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.RoutingKeyName
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.RoutingKeyName.ROUTING_KEY_REGEXP
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.NonTerminalException
import com.exactpro.th2.infraoperator.util.CustomResourceUtils
import com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor
import mu.KotlinLogging

object BindQueueLinkResolver {
    private val logger = KotlinLogging.logger { }

    @JvmStatic
    fun resolveDeclaredLinks(resource: Th2CustomResource) {
        val namespace = resource.metadata.namespace
        val resourceName = resource.metadata.name
        val commitHash = CustomResourceUtils.extractShortCommitHash(resource)
        for (subscriberPin in resource.spec.pins.mq.subscribers) {
            val queueName = QueueName(namespace, resourceName, subscriberPin.name)
            for ((box, pin) in subscriberPin.linkTo) {
                val linkDescription = LinkDescription(
                    queueName,
                    RoutingKeyName(namespace, box, pin),
                    namespace
                )
                bindQueues(linkDescription, commitHash)
            }
            removeExtinctBindings(queueName, subscriberPin.linkTo, commitHash)
        }
    }

    @JvmStatic
    fun resolveHiddenLinks(resource: Th2CustomResource) {
        val namespace = resource.metadata.namespace
        val resourceName = resource.metadata.name
        val resourceLabel = annotationFor(resource)
        val commitHash = CustomResourceUtils.extractShortCommitHash(resource)
        if (resourceName.equals(EVENT_STORAGE_BOX_ALIAS) || resourceName.equals(MESSAGE_STORAGE_BOX_ALIAS)) {
            return
        }
        // create event storage link for each resource
        val estoreLinkDescription = LinkDescription(
            QueueName(namespace, EVENT_STORAGE_BOX_ALIAS, EVENT_STORAGE_PIN_ALIAS),
            RoutingKeyName(namespace, resourceName, EVENT_STORAGE_PIN_ALIAS),
            namespace
        )
        bindQueues(estoreLinkDescription, commitHash)

        val currentLinks: MutableList<Link> = ArrayList()
        val queueName = QueueName(namespace, MESSAGE_STORAGE_BOX_ALIAS, MESSAGE_STORAGE_PIN_ALIAS)
        // create message store link for only resources that need it
        for ((pinName, attributes) in resource.spec.pins.mq.publishers) {
            if (checkStorePinAttributes(attributes, resourceLabel, pinName)) {
                val mstoreLinkDescription = LinkDescription(
                    queueName,
                    RoutingKeyName(namespace, resourceName, pinName),
                    namespace
                )
                bindQueues(mstoreLinkDescription, commitHash)
                currentLinks.add(Link(resourceName, pinName))
            }
        }
        removeExtinctBindings(queueName, currentLinks, commitHash, resourceName)
    }

    private fun checkStorePinAttributes(attributes: Set<String>, resourceLabel: String, pinName: String?): Boolean {
        if (!attributes.contains(PinAttribute.store.name)) {
            return false
        }
        if (attributes.contains(PinAttribute.parsed.name)) {
            logger.warn(
                "Detected a pin: {}:{} with incorrect store configuration. attribute 'parsed' not allowed",
                resourceLabel,
                pinName
            )
            return false
        }
        if (!attributes.contains(PinAttribute.raw.name)) {
            logger.warn(
                "Detected a pin: {}:{} with incorrect store configuration. attribute 'raw' is missing",
                resourceLabel,
                pinName
            )
            return false
        }
        return true
    }

    private fun bindQueues(queue: LinkDescription, commitHash: String) {
        try {
            val channel = RabbitMQContext.getChannel()
            val queueName = queue.queueName.toString()
            val currentQueue = RabbitMQContext.getQueue(queueName)
            if (currentQueue == null) {
                logger.info("Queue '{}' does not yet exist. skipping binding", queueName)
                return
            }
            channel.queueBind(queue.queueName.toString(), queue.exchange, queue.routingKey.toString())
            logger.info(
                "Queue '{}' successfully bound to '{}' (commit-{})",
                queueName,
                queue.routingKey.toString(),
                commitHash
            )
        } catch (e: Exception) {
            val message = "Exception while working with rabbitMq"
            logger.error(message, e)
            throw NonTerminalException(message, e)
        }
    }

    private fun removeExtinctBindings(
        queue: QueueName,
        currentLinks: List<Link>,
        commitHash: String,
        resName: String? = null
    ) {
        val queueName = queue.toString()
        val bindingOnRabbit = RabbitMQContext.getQueueBindings(queueName)
            ?.map { it.routingKey }
            ?.filter {
                it.matches(ROUTING_KEY_REGEXP.toRegex()) &&
                    if (resName == null) true else RoutingKeyName.fromString(it).boxName == resName
            }
        val currentBindings = currentLinks.mapTo(HashSet()) {
            RoutingKeyName(queue.namespace, it.box, it.pin).toString()
        }
        try {
            val channel = RabbitMQContext.getChannel()
            bindingOnRabbit?.forEach {
                if (!currentBindings.contains(it)) {
                    val currentQueue = RabbitMQContext.getQueue(queueName)
                    if (currentQueue == null) {
                        logger.info("Queue '{}' already removed. skipping unbinding", queueName)
                        return
                    }
                    channel.queueUnbind(queueName, queue.namespace, it)
                    logger.info("Unbind queue '{}' -> '{}'. (commit-{})", it, queueName, commitHash)
                }
            }
        } catch (e: Exception) {
            val message = "Exception while removing extinct bindings"
            logger.error(message, e)
            throw NonTerminalException(message, e)
        }
    }
}
