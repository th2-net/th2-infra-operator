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

package com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq;

import com.exactpro.th2.infraoperator.model.box.schema.link.EnqueuedLink;
import com.exactpro.th2.infraoperator.model.box.schema.link.QueueDescription;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingMQ;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.QueueName;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.RoutingKeyName;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.NonTerminalException;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class BindQueueLinkResolver {

    private static final Logger logger = LoggerFactory.getLogger(BindQueueLinkResolver.class);

    public static void resolveBoxResource(String namespace,
                                          List<Th2Link> linkResources,
                                          Th2CustomResource affectedResource) {

        String affectedResourceName = affectedResource.getMetadata().getName();

        for (var lRes : linkResources) {
            for (var pinCouple : lRes.getSpec().getBoxesRelation().getRouterMq()) {
                var queueBunch = new QueueDescription(
                        new QueueName(namespace, pinCouple.getTo()),
                        new RoutingKeyName(namespace, pinCouple.getFrom()),
                        namespace
                );

                if (affectedResourceName.equals(pinCouple.getFrom().getBoxName())
                        || affectedResourceName.equals(pinCouple.getTo().getBoxName())) {
                    bindQueues(namespace, queueBunch);
                }
            }
        }
    }

    public static void resolveLinkResource(String namespace,
                                           Th2Link oldLinkRes,
                                           Th2Link newLinkRes) {

        for (var pinCouple : newLinkRes.getSpec().getBoxesRelation().getRouterMq()) {
            var queueBunch = new QueueDescription(
                    new QueueName(namespace, pinCouple.getTo()),
                    new RoutingKeyName(namespace, pinCouple.getFrom()),
                    namespace
            );
            bindQueues(namespace, queueBunch);
        }
        removeExtinctBindings(namespace, oldLinkRes, newLinkRes);
    }

    private static List<EnqueuedLink> convert(List<PinCouplingMQ> links, String namespace, String exchange) {
        List<EnqueuedLink> mqEnqueuedLinks = new ArrayList<>();
        for (var pinCouple : links) {
            var queueBunch = new QueueDescription(
                    new QueueName(namespace, pinCouple.getTo()),
                    new RoutingKeyName(namespace, pinCouple.getFrom()),
                    exchange
            );
            mqEnqueuedLinks.add(new EnqueuedLink(pinCouple, queueBunch));
        }
        return mqEnqueuedLinks;
    }

    @SneakyThrows
    private static void bindQueues(String namespace, QueueDescription queue) {

        try {
            Channel channel = RabbitMQContext.getChannel();

            if (!channel.isOpen()) {
                logger.warn("RabbitMQ connection is broken, trying to reconnect...");
                RabbitMQContext.closeChannel();
                channel = RabbitMQContext.getChannel();
                logger.info("RabbitMQ connection has been restored");
            }

            String queueName = queue.getQueueName().toString();
            var currentQueue = RabbitMQContext.getQueue(queueName);
            if (currentQueue == null) {
                logger.info("Queue '{}' does not yet exist. skipping binding", queueName);
                return;
            }
            channel.queueBind(queue.getQueueName().toString(), queue.getExchange(), queue.getRoutingKey().toString());
            logger.info("Queue '{}' successfully bound to '{}'", queueName, queue.getRoutingKey().toString());
        } catch (Exception e) {
            String message = "Exception while working with rabbitMq";
            logger.error(message, e);
            throw new NonTerminalException(message, e);
        }
    }

    @SneakyThrows
    private static void removeExtinctBindings(String namespace,
                                              Th2Link oldLinkRes,
                                              Th2Link newLinkRes) {
        removeExtinctBindings(namespace,
                oldLinkRes.getSpec().getBoxesRelation().getRouterMq(),
                newLinkRes.getSpec().getBoxesRelation().getRouterMq());
    }

    @SneakyThrows
    public static void removeExtinctBindings(String namespace,
                                             List<PinCouplingMQ> oldLinksCoupling,
                                             List<PinCouplingMQ> newLinksCoupling) {

        String exchange = namespace;

        List<EnqueuedLink> oldLinks = convert(oldLinksCoupling, namespace, exchange);
        List<EnqueuedLink> newLinks = convert(newLinksCoupling, namespace, exchange);

        oldLinks.removeIf(qlb -> newLinks.stream().anyMatch(newQlb ->
                qlb.getQueueDescription().getQueueName().equals(newQlb.getQueueDescription().getQueueName())
                        && qlb.getQueueDescription().getRoutingKey()
                        .equals(newQlb.getQueueDescription().getRoutingKey())
        ));
        try {
            Channel channel = RabbitMQContext.getChannel();

            if (!channel.isOpen()) {
                logger.warn("RabbitMQ connection is broken, trying to reconnect...");
                RabbitMQContext.closeChannel();
                channel = RabbitMQContext.getChannel();
                logger.info("RabbitMQ connection has been restored");
            }

            for (var extinctLink : oldLinks) {

                var fromBox = extinctLink.getFrom();
                var toBox = extinctLink.getTo();
                QueueDescription queueBunch = extinctLink.getQueueDescription();
                var queueName = queueBunch.getQueueName().toString();
                var routingKey = queueBunch.getRoutingKey().toString();

                var currentQueue = RabbitMQContext.getQueue(queueName);
                if (currentQueue == null) {
                    logger.info("Queue '{}' already removed. skipping unbinding", queueName);
                    return;
                }
                channel.queueUnbind(queueName, queueBunch.getExchange(), routingKey);

                String infoMsg = String.format(
                        "Unbind queue '%1$s' -> '%2$s' because link {%5$s.%3$s -> %5$s.%4$s} is not active anymore",
                        queueName, routingKey, fromBox, toBox, namespace
                );

                logger.info(infoMsg);
            }
        } catch (Exception e) {
            String message = "Exception while working with rabbitMq";
            logger.error(message, e);
            throw new NonTerminalException(message, e);
        }
    }
}
