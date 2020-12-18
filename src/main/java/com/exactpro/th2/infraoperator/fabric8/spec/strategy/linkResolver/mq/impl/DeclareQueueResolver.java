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
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.queue.QueueName;
import com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils;
import com.rabbitmq.client.Channel;
import com.rabbitmq.http.client.domain.QueueInfo;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils.extractName;

public class DeclareQueueResolver {

    private static final Logger logger = LoggerFactory.getLogger(DeclareQueueResolver.class);
    private final RabbitMQManagementConfig rabbitMQManagementConfig;

    @SneakyThrows
    public DeclareQueueResolver() {
        this.rabbitMQManagementConfig = OperatorConfig.INSTANCE.getRabbitMQManagementConfig();
    }

    public void resolveAdd(Th2CustomResource resource) {

        String namespace = ExtractUtils.extractNamespace(resource);
        RabbitMQContext.createVHostIfAbsent(namespace, rabbitMQManagementConfig);
        declareQueueBunch(namespace, resource);
    }

    public void resolveDelete(Th2CustomResource resource) {

        String namespace = ExtractUtils.extractNamespace(resource);
        Channel channel = getChannel(namespace);
        //get queues that are associated with current box and are not linked through Th2Link resources
        Set<String> boxUnlinkedQueueNames = getBoxUnlinkedQueues(namespace, extractName(resource));
        removeExtinctQueues(channel, boxUnlinkedQueueNames);
    }

    @SneakyThrows
    private void declareQueueBunch(String namespace, Th2CustomResource resource) {

        Channel channel = getChannel(namespace);
        String exchangeName = RabbitMQContext.getExchangeName(namespace);

        channel.exchangeDeclare(exchangeName, "direct", rabbitMQManagementConfig.isPersistence());
        //get queues that are associated with current box and are not linked through Th2Link resources
        Set<String> boxUnlinkedQueueNames = getBoxUnlinkedQueues(namespace, extractName(resource));

        for (var pin : ExtractUtils.extractMqPins(resource)) {
            var attrs = pin.getAttributes();
            String boxName = extractName(resource);
            BoxMq boxMq = BoxMq.builder()
                    .box(boxName)
                    .pin(pin.getName())
                    .build();

            if (!attrs.contains(DirectionAttribute.publish.name())) {
                String queueName = new QueueName(namespace, boxMq).toString();
                //remove from set if pin for queue still exists.
                boxUnlinkedQueueNames.remove(queueName);
                var declareResult = channel.queueDeclare(queueName
                        , rabbitMQManagementConfig.isPersistence()
                        , false
                        , false
                        , RabbitMQContext.generateQueueArguments(pin.getSettings()));
                logger.info("Queue '{}' of resource {} was successfully declared", declareResult.getQueue(), extractName(resource));
            }
        }
        //remove from rabbit queues that are left i.e. inactive
        removeExtinctQueues(channel, boxUnlinkedQueueNames);
    }


    private Set<String> getBoxUnlinkedQueues(String namespace, String boxQueuesFullName) {
        Set<String> boxQueueNames = getBoxQueues(namespace, boxQueuesFullName);
        removeLinkedQueues(boxQueueNames, namespace);
        return boxQueueNames;
    }

    @SneakyThrows
    private Set<String> getBoxQueues(String namespace, String boxName) {
        RabbitMQConfig rabbitMQConfig = RabbitMQContext.getRabbitMQConfig(namespace);
        List<QueueInfo> queueInfoList = RabbitMQContext.getQueues(rabbitMQConfig.getVHost(), rabbitMQManagementConfig);

        List<QueueName> queueNames = queueInfoList.stream()
                .map(queueInfo -> QueueName.fromString(queueInfo.getName()))
                .collect(Collectors.toList());

        return queueNames.stream()
                .filter(queueName -> queueName != null && queueName.getBoxName().equals(boxName))
                .map(QueueName::toString)
                .collect(Collectors.toSet());
    }

    private void removeLinkedQueues(Set<String> boxQueueNames, String namespace) {
        var lSingleton = LinkSingleton.INSTANCE;
        var mqActiveLinks = new ArrayList<>(lSingleton.getMqActiveLinks(namespace));
        //remove queues that appear in active links
        Set<String> activeQueueNames = mqActiveLinks.stream()
                .map(mqActiveLink -> mqActiveLink.getQueueBunch().getQueue())
                .collect(Collectors.toSet());
        boxQueueNames.removeAll(activeQueueNames);
    }

    @SneakyThrows
    private Channel getChannel(String namespace) {
        Channel channel = RabbitMQContext.getChannel(namespace);
        if (!channel.isOpen()) {
            logger.warn("RabbitMQ connection is broken, trying to reconnect...");
            RabbitMQContext.closeChannel(namespace);
            channel = RabbitMQContext.getChannel(namespace);
            logger.info("RabbitMQ connection has been restored");
        }

        String exchangeName = RabbitMQContext.getExchangeName(namespace);
        if (!RabbitMQContext.isExchangeReset(namespace)) {
            logger.info("Deleting exchange in vHost: {}", namespace);
            channel.exchangeDelete(exchangeName);
            RabbitMQContext.markExchangeReset(namespace);
        }
        return channel;
    }


    private void removeExtinctQueues(Channel channel, Set<String> extinctQueueNames) {
        if (!extinctQueueNames.isEmpty()) {
            logger.info("Unlinked pin(s) removed from resource. trying to delete queues associated with it");
            extinctQueueNames.forEach(queueName -> {
                try {
                    channel.queueDelete(queueName);
                    logger.info("Deleted queue: [{}]", queueName);
                } catch (IOException e) {
                    logger.error("Exception deleting queue: [{}]", queueName, e);
                }
            });
        }

    }

}
