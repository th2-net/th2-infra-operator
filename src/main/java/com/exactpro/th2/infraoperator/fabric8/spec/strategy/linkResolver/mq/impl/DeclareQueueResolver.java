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
import com.exactpro.th2.infraoperator.fabric8.spec.link.singleton.LinkSingleton;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.DirectionAttribute;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.Queue;
import com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils;
import com.exactpro.th2.infraoperator.fabric8.util.MqVHostUtils;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.http.client.domain.QueueInfo;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.mq.impl.RabbitMqStaticContext.*;
import static com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils.*;
import static com.exactpro.th2.infraoperator.fabric8.util.MqVHostUtils.getQueues;

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
        MqVHostUtils.createVHostIfAbsent(namespace, rabbitMQManagementConfig);
        createChannelIfAbsent(namespace, rabbitMQManagementConfig, connectionFactory);
        declareQueueBunch(namespace, resource);

    }

    public void resolveDelete(Th2CustomResource resource) {
        String namespace = ExtractUtils.extractNamespace(resource);
        RabbitMQConfig rabbitMQConfig = getRabbitMQConfig(namespace);
        Channel channel = getChannel(namespace, rabbitMQConfig);
        //get queues that are associated with current box and are not linked through Th2Link resources
        List<Queue> boxUnlinkedQueues = getBoxUnlinkedQueues(namespace, extractName(resource));
        removeExtinctQueues(channel, boxUnlinkedQueues);
    }

    @SneakyThrows
    private void declareQueueBunch(String namespace, Th2CustomResource resource) {
        RabbitMQConfig rabbitMQConfig = getRabbitMQConfig(namespace);
        Channel channel = getChannel(namespace, rabbitMQConfig);
        channel.exchangeDeclare(rabbitMQConfig.getExchangeName(), "direct", rabbitMQManagementConfig.isPersistence());
        //get queues that are associated with current box and are not linked through Th2Link resources
        List<Queue> boxUnlinkedQueues = getBoxUnlinkedQueues(namespace, extractName(resource));

        for (var pin : ExtractUtils.extractMqPins(resource)) {
            var attrs = pin.getAttributes();
            String boxName = extractName(resource);
            BoxMq boxMq = BoxMq.builder()
                    .box(boxName)
                    .pin(pin.getName())
                    .build();

            if (!attrs.contains(DirectionAttribute.publish.name())) {
                String queue = buildQueue(namespace, boxMq);
                //remove from list if pin for queue still exists.
                boxUnlinkedQueues.removeIf(oldQueue -> oldQueue.toQueueString().equals(queue));
                var declareResult = channel.queueDeclare(queue, rabbitMQManagementConfig.isPersistence(), false, false, generateQueueArguments(pin.getSettings()));
                logger.info("Queue '{}' of resource {} was successfully declared", declareResult.getQueue(), extractName(resource));
            }
        }
        //remove queues that are left i.e. inactive
        removeExtinctQueues(channel, boxUnlinkedQueues);
    }


    private List<Queue> getBoxUnlinkedQueues(String namespace, String boxQueuesFullName) {
        List<Queue> boxQueues = getBoxQueues(namespace, boxQueuesFullName);
        removeLinkedQueues(boxQueues, namespace);
        return boxQueues;
    }

    @SneakyThrows
    private List<Queue> getBoxQueues(String namespace, String boxName) {
        RabbitMQConfig rabbitMQConfig = getRabbitMQConfig(namespace);
        List<QueueInfo> queueInfoList = getQueues(rabbitMQConfig.getVHost(), rabbitMQManagementConfig);

        List<Queue> queues = queueInfoList.stream()
                .map(queueInfo -> Queue.parseQueue(queueInfo.getName()))
                .collect(Collectors.toList());

        return queues.stream()
                .filter(queue -> queue.getBox().equals(boxName))
                .collect(Collectors.toList());
    }

    private void removeLinkedQueues(List<Queue> boxQueues, String namespace) {
        var lSingleton = LinkSingleton.INSTANCE;
        var mqActiveLinks = new ArrayList<>(lSingleton.getMqActiveLinks(namespace));
        //remove queues that appear in links
        boxQueues.removeIf(boxQueue -> mqActiveLinks.stream().anyMatch(mqActiveLink -> boxQueue.toQueueString().equals(mqActiveLink.getQueueBunch().getQueue())));
    }

    @SneakyThrows
    private Channel getChannel(String namespace, RabbitMQConfig rabbitMQConfig) {
        Map<String, RabbitMqStaticContext.ChannelBunch> channelBunchMap = getMqChannels();
        Channel channel = channelBunchMap.get(namespace).getChannel();
        if (!channel.isOpen()) {
            logger.warn("RMQ connection is broken, trying to reconnect...");
            channelBunchMap.remove(namespace);
            createChannelIfAbsent(namespace, rabbitMQManagementConfig, connectionFactory);
            channel = channelBunchMap.get(namespace).getChannel();
            logger.info("RMQ connection has been restored");
        }
        var exchangeReset = getMqExchangeResets().get(namespace);
        if (exchangeReset == null || !exchangeReset) {
            logger.info("Deleting exchange in vHost: {}", namespace);
            channel.exchangeDelete(rabbitMQConfig.getExchangeName());
            getMqExchangeResets().put(namespace, true);
        }
        return channel;
    }


    private void removeExtinctQueues(Channel channel, List<Queue> extinctQueues) {
        if (!extinctQueues.isEmpty()) {
            logger.info("Unlinked pin(s) removed from resource. trying to delete queues associated with it");
            extinctQueues.forEach(queue -> {
                try {
                    channel.queueDelete(queue.toQueueString());
                    logger.info("Deleted queue: [{}]", queue.toQueueString());
                } catch (IOException e) {
                    logger.error("Exception deleting queue: [{}]", queue.toQueueString(), e);
                }
            });
        }

    }


    private String buildQueue(String namespace, BoxMq boxMq) {
        return new Queue(boxMq, namespace).toQueueString();
    }
}
