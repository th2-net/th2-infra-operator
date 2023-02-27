/*
 * Copyright 2020-2022 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.infraoperator.model.box.mq.factory

import com.exactpro.th2.infraoperator.configuration.ConfigLoader
import com.exactpro.th2.infraoperator.model.LinkDescription
import com.exactpro.th2.infraoperator.model.box.mq.MessageRouterConfiguration
import com.exactpro.th2.infraoperator.model.box.mq.QueueConfiguration
import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS
import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.MESSAGE_STORAGE_PIN_ALIAS
import com.exactpro.th2.infraoperator.spec.Th2CustomResource
import com.exactpro.th2.infraoperator.spec.shared.PinAttribute
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.QueueName
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.RoutingKeyName
import com.exactpro.th2.infraoperator.util.ExtractUtils

/**
 * A factory that creates a mq configuration
 * based on the th2 resource and a list of active links.
 */
class MessageRouterConfigFactoryMstore : MessageRouterConfigFactory() {
    /**
     * Creates a mq configuration based on the th2 resource and a list of active links.
     *
     * @param resource th2 resource containing a list of [PinSpec]s
     * @return ready mq configuration based on active `links` and specified links in `resource`
     */
    @Override
    override fun createConfig(resource: Th2CustomResource): MessageRouterConfiguration {
        val queues: MutableMap<String, QueueConfiguration> = HashMap()
        val boxName = ExtractUtils.extractName(resource)
        val namespace = ExtractUtils.extractNamespace(resource)

        // add event storage pin config for each resource
        queues[EVENT_STORAGE_PIN_ALIAS] = generatePublishToEstorePin(namespace, boxName)
        queues[MESSAGE_STORAGE_PIN_ALIAS] = QueueConfiguration(
            LinkDescription(
                QueueName(namespace, boxName, MESSAGE_STORAGE_PIN_ALIAS),
                RoutingKeyName.EMPTY,
                namespace
            ),
            setOf(PinAttribute.subscribe.name, PinAttribute.raw.name),
            emptyList()
        )

        val globalExchange = ConfigLoader.config.rabbitMQManagement.exchangeName
        return MessageRouterConfiguration(queues, globalExchange)
    }
}
