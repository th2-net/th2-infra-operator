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
import com.exactpro.th2.infraoperator.fabric8.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.boxes.box.impl.BoxMq;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.DirectionAttribute;
import com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils;
import com.exactpro.th2.infraoperator.fabric8.util.MqVHostUtils;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.mq.impl.RabbitMqStaticContext.*;

public class DeclareQueueResolver {

    private static final Logger logger = LoggerFactory.getLogger(DeclareQueueResolver.class);

    private final ConnectionFactory connectionFactory;

    private final OperatorConfig.MqGlobalConfig mqGlobalConfig;

    @SneakyThrows
    public DeclareQueueResolver() {
        this.mqGlobalConfig = OperatorConfig.INSTANCE.getMqAuthConfig();
        this.connectionFactory = new ConnectionFactory();
    }

    public void resolveAdd(Th2CustomResource resource) {

        String namespace = ExtractUtils.extractNamespace(resource);

        MqVHostUtils.createVHostIfAbsent(namespace, mqGlobalConfig);
        createChannelIfAbsent(namespace, mqGlobalConfig, connectionFactory);
        declareQueueBunch(namespace, resource);

    }

    public void resolveDelete(Th2CustomResource resource) {
        // TODO implement
    }

    @SneakyThrows
    private void declareQueueBunch(String namespace, Th2CustomResource resource) {

        OperatorConfig.MqWorkSpaceConfig wsConfig = getWsConfig(namespace);

        Map<String, RabbitMqStaticContext.ChannelBunch> channelBunchMap = getMqChannels();

        Channel channel =  channelBunchMap.get(namespace).getChannel();

        if (!channel.isOpen()) {
            logger.warn("RMQ connection is broken, trying to reconnect...");
            channelBunchMap.remove(namespace);
            createChannelIfAbsent(namespace, mqGlobalConfig, connectionFactory);
            channel = channelBunchMap.get(namespace).getChannel();
            logger.info("RMQ connection has been restored");
        }

        var exchangeReset = getMqExchangeResets().get(namespace);

        if (exchangeReset == null || !exchangeReset) {
            channel.exchangeDelete(wsConfig.getExchangeName());
            getMqExchangeResets().put(namespace, true);
        }

        channel.exchangeDeclare(wsConfig.getExchangeName(), "direct", mqGlobalConfig.isPersistence());

        for (var pin : ExtractUtils.extractMqPins(resource)) {

            var attrs = pin.getAttributes();

            String boxName = ExtractUtils.extractName(resource);

            BoxMq boxMq = BoxMq.builder()
                    .box(boxName)
                    .pin(pin.getName())
                    .build();

            if (!attrs.contains(DirectionAttribute.publish.name())) {
                String queue = buildQueue(namespace, boxMq);
                var declareResult = channel.queueDeclare(queue, mqGlobalConfig.isPersistence(), false, false, generateQueueArguments(pin.getSettings()));
                logger.info("Queue '{}' of resource {} was successfully declared", declareResult.getQueue(), ExtractUtils.extractName(resource));
            }
        }
    }

    private String buildQueue(String namespace, BoxMq boxMq) {
        return OperatorConfig.QUEUE_PREFIX + namespace + "_" + boxMq.toString();
    }
}
