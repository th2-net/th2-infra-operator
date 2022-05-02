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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.extractShortCommitHash;

public class BindQueueLinkResolver {

    private static final Logger logger = LoggerFactory.getLogger(BindQueueLinkResolver.class);

    public static void resolveBoxResource(String namespace,
                                          List<Th2Link> linkResources,
                                          Th2CustomResource affectedResource) {

        String affectedResourceName = affectedResource.getMetadata().getName();
        String commitHash = extractShortCommitHash(affectedResource);

        for (var lRes : linkResources) {
            for (var pinCouple : lRes.getSpec().getBoxesRelation().getRouterMq()) {
                var queueBunch = new QueueDescription(
                        new QueueName(namespace, pinCouple.getTo()),
                        new RoutingKeyName(namespace, pinCouple.getFrom()),
                        namespace
                );

                if (affectedResourceName.equals(pinCouple.getFrom().getBoxName())
                        || affectedResourceName.equals(pinCouple.getTo().getBoxName())) {
                    bindQueues(queueBunch, commitHash);
                }
            }
        }
    }

    public static void resolveLinkResource(String namespace,
                                           Th2Link oldLinkRes,
                                           Th2Link newLinkRes) {

        String commitHash = extractShortCommitHash(newLinkRes);

        for (var pinCouple : newLinkRes.getSpec().getBoxesRelation().getRouterMq()) {
            var queueBunch = new QueueDescription(
                    new QueueName(namespace, pinCouple.getTo()),
                    new RoutingKeyName(namespace, pinCouple.getFrom()),
                    namespace
            );
            bindQueues(queueBunch, commitHash);
        }
        removeExtinctBindings(namespace, oldLinkRes, newLinkRes, commitHash);
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

    private static void bindQueues(QueueDescription queue, String commitHash) {

        try {
            Channel channel = RabbitMQContext.getChannel();

            String queueName = queue.getQueueName().toString();
            var currentQueue = RabbitMQContext.getQueue(queueName);
            if (currentQueue == null) {
                logger.info("Queue '{}' does not yet exist. skipping binding", queueName);
                return;
            }
            channel.queueBind(queue.getQueueName().toString(), queue.getExchange(), queue.getRoutingKey().toString());
            logger.info("Queue '{}' successfully bound to '{}' (commit-{})",
                    queueName, queue.getRoutingKey().toString(), commitHash);
        } catch (Exception e) {
            String message = "Exception while working with rabbitMq";
            logger.error(message, e);
            throw new NonTerminalException(message, e);
        }
    }

    private static void removeExtinctBindings(String namespace,
                                              Th2Link oldLinkRes,
                                              Th2Link newLinkRes,
                                              String commitHash) {
        removeExtinctBindings(namespace,
                oldLinkRes.getSpec().getBoxesRelation().getRouterMq(),
                newLinkRes.getSpec().getBoxesRelation().getRouterMq(),
                commitHash);
    }

    public static void removeExtinctBindings(String namespace,
                                             List<PinCouplingMQ> oldLinksCoupling,
                                             List<PinCouplingMQ> newLinksCoupling,
                                             String commitHash) {

        List<EnqueuedLink> oldLinks = convert(oldLinksCoupling, namespace, namespace);
        List<EnqueuedLink> newLinks = convert(newLinksCoupling, namespace, namespace);

        oldLinks.removeIf(qlb -> newLinks.stream().anyMatch(newQlb ->
                qlb.getQueueDescription().getQueueName().equals(newQlb.getQueueDescription().getQueueName())
                        && qlb.getQueueDescription().getRoutingKey()
                        .equals(newQlb.getQueueDescription().getRoutingKey())
        ));
        try {
            Channel channel = RabbitMQContext.getChannel();

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
                        "Unbind queue '%1$s' -> '%2$s'. link {%5$s.%3$s -> %5$s.%4$s} is not active (commit-%6$s)",
                        queueName, routingKey, fromBox, toBox, namespace, commitHash
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
