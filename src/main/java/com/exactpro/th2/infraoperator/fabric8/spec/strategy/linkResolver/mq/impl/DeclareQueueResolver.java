/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.mq.impl;

import com.exactpro.th2.infraoperator.fabric8.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.fabric8.configuration.RabbitMQConfig;
import com.exactpro.th2.infraoperator.fabric8.configuration.RabbitMQManagementConfig;
import com.exactpro.th2.infraoperator.fabric8.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.boxes.box.impl.BoxMq;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.DirectionAttribute;
import com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeclareQueueResolver {

    private static final Logger logger = LoggerFactory.getLogger(DeclareQueueResolver.class);

    private final ConnectionFactory connectionFactory;

    private final RabbitMQManagementConfig rabbitMQManagementConfig;

    @SneakyThrows
    public DeclareQueueResolver() {
        this.rabbitMQManagementConfig = OperatorConfig.INSTANCE.getRabbitMQManagementConfig();
        this.connectionFactory = new ConnectionFactory();
    }

    public void resolveAdd(Th2CustomResource resource) {

        String namespace = ExtractUtils.extractNamespace(resource);

        RabbitMQContext.createVHostIfAbsent(namespace, rabbitMQManagementConfig);
        declareQueueBunch(namespace, resource);
    }

    public void resolveDelete(Th2CustomResource resource) {
        // TODO implement
    }

    @SneakyThrows
    private void declareQueueBunch(String namespace, Th2CustomResource resource) {

        RabbitMQConfig rabbitMQConfig = RabbitMQContext.getRabbitMQConfig(namespace);
        Channel channel = RabbitMQContext.getChannel(namespace);

        if (channel == null || !channel.isOpen()) {
            logger.warn("RMQ connection is broken, trying to reconnect...");
            RabbitMQContext.closeChannel(namespace);
            channel = RabbitMQContext.createChannelIfAbsent(namespace, rabbitMQManagementConfig, connectionFactory);
            logger.info("RMQ connection has been restored");
        }

        if (!RabbitMQContext.isExchangeReset(namespace)) {
            channel.exchangeDelete(rabbitMQConfig.getExchangeName());
            RabbitMQContext.markExchangeReset(namespace);
        }

        channel.exchangeDeclare(rabbitMQConfig.getExchangeName(), "direct", rabbitMQManagementConfig.isPersistence());

        for (var pin : ExtractUtils.extractMqPins(resource)) {

            var attrs = pin.getAttributes();

            String boxName = ExtractUtils.extractName(resource);

            BoxMq boxMq = BoxMq.builder()
                .box(boxName)
                .pin(pin.getName())
                .build();

            if (!attrs.contains(DirectionAttribute.publish.name())) {
                String queue = buildQueue(namespace, boxMq);
                var declareResult = channel.queueDeclare(queue, rabbitMQManagementConfig.isPersistence(),
                    false, false, RabbitMQContext.generateQueueArguments(pin.getSettings()));
                logger.info("Queue '{}' of resource {} was successfully declared",
                    declareResult.getQueue(), ExtractUtils.extractName(resource));
            }
        }
    }

    private String buildQueue(String namespace, BoxMq boxMq) {
        return OperatorConfig.QUEUE_PREFIX + namespace + "_" + boxMq.toString();
    }
}
