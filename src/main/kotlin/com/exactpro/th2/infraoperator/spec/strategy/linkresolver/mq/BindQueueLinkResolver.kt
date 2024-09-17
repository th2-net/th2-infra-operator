/*
 * Copyright 2020-2024 Exactpro (Exactpro Systems Limited)
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
import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.MESSAGE_STORAGE_BOX_ALIAS
import com.exactpro.th2.infraoperator.spec.Th2CustomResource
import com.exactpro.th2.infraoperator.spec.shared.PinAttribute
import com.exactpro.th2.infraoperator.spec.shared.pin.Link
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.createEstoreQueueName
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.createEstoreRoutingKeyName
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.createMstoreQueueName
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.QueueName
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.RoutingKeyName
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.RoutingKeyName.ROUTING_KEY_REGEXP
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.NonTerminalException
import com.exactpro.th2.infraoperator.util.CustomResourceUtils
import com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor
import io.github.oshai.kotlinlogging.KotlinLogging

class BindQueueLinkResolver(
    private val rabbitMQContext: RabbitMQContext,
) {
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
            createEstoreQueueName(namespace),
            createEstoreRoutingKeyName(namespace, resourceName),
            namespace
        )
        bindQueues(estoreLinkDescription, commitHash)

        val currentLinks: MutableList<Link> = ArrayList()
        val queueName = createMstoreQueueName(namespace)
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
            K_LOGGER.warn {
                "Detected a pin: $resourceLabel:$pinName with incorrect store configuration. attribute 'parsed' not allowed"
            }
            return false
        }
        if (!attributes.contains(PinAttribute.raw.name)) {
            K_LOGGER.warn {
                "Detected a pin: $resourceLabel:$pinName with incorrect store configuration. attribute 'raw' is missing"
            }
            return false
        }
        return true
    }

    private fun bindQueues(queue: LinkDescription, commitHash: String) {
        try {
            val channel = rabbitMQContext.channel
            val queueName = queue.queueName.toString()
            val currentQueue = rabbitMQContext.getQueue(queueName)
            if (currentQueue == null) {
                K_LOGGER.info {"Queue '$queueName' does not yet exist. skipping binding" }
                return
            }
            channel.queueBind(queue.queueName.toString(), queue.exchange, queue.routingKey.toString())
            K_LOGGER.info {
                "Queue '$queueName' successfully bound to '${queue.routingKey}' (commit-$commitHash)"
            }
        } catch (e: Exception) {
            val message = "Exception while working with rabbitMq"
            K_LOGGER.error(e) { message }
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
        val bindingOnRabbit = rabbitMQContext.getQueueBindings(queueName)
            ?.map { it.routingKey }
            ?.filter {
                it.matches(ROUTING_KEY_REGEXP.toRegex()) &&
                    if (resName == null) true else RoutingKeyName.fromString(it).boxName == resName
            }
        val currentBindings = currentLinks.mapTo(HashSet()) {
            RoutingKeyName(queue.namespace, it.box, it.pin).toString()
        }
        try {
            val channel = rabbitMQContext.channel
            bindingOnRabbit?.forEach {
                if (!currentBindings.contains(it)) {
                    val currentQueue = rabbitMQContext.getQueue(queueName)
                    if (currentQueue == null) {
                        K_LOGGER.info { "Queue '$queueName' already removed. skipping unbinding" }
                        return
                    }
                    channel.queueUnbind(queueName, queue.namespace, it)
                    K_LOGGER.info { "Unbind queue '$it' -> '$queueName'. (commit-$commitHash)" }
                }
            }
        } catch (e: Exception) {
            val message = "Exception while removing extinct bindings"
            K_LOGGER.error(e) { message }
            throw NonTerminalException(message, e)
        }
    }

    companion object {
        private val K_LOGGER = KotlinLogging.logger { }
    }
}
