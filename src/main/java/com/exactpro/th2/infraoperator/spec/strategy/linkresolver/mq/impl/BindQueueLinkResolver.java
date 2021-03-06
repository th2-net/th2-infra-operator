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

package com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.impl;

import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.configuration.RabbitMQManagementConfig;
import com.exactpro.th2.infraoperator.model.box.schema.link.EnqueuedLink;
import com.exactpro.th2.infraoperator.model.box.schema.link.QueueDescription;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingMQ;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinMQ;
import com.exactpro.th2.infraoperator.spec.link.validator.ValidationStatus;
import com.exactpro.th2.infraoperator.spec.link.validator.chain.impl.ExpectedPinAttr;
import com.exactpro.th2.infraoperator.spec.link.validator.chain.impl.ExpectedPinType;
import com.exactpro.th2.infraoperator.spec.link.validator.chain.impl.PinExist;
import com.exactpro.th2.infraoperator.spec.link.validator.chain.impl.ResourceExist;
import com.exactpro.th2.infraoperator.spec.link.validator.model.DirectionalLinkContext;
import com.exactpro.th2.infraoperator.spec.shared.BoxDirection;
import com.exactpro.th2.infraoperator.spec.shared.SchemaConnectionType;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.GenericLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.QueueLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.QueueName;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.RoutingKeyName;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.NonTerminalException;
import com.exactpro.th2.infraoperator.spec.strategy.resfinder.box.BoxResourceFinder;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import com.rabbitmq.client.Channel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class BindQueueLinkResolver extends GenericLinkResolver<EnqueuedLink> implements QueueLinkResolver {

    private static final Logger logger = LoggerFactory.getLogger(BindQueueLinkResolver.class);

    private final BoxResourceFinder resourceFinder;

    private final RabbitMQManagementConfig rabbitMQManagementConfig;

    @SneakyThrows
    public BindQueueLinkResolver(BoxResourceFinder resourceFinder) {
        this.rabbitMQManagementConfig = OperatorConfig.INSTANCE.getRabbitMQManagementConfig();
        this.resourceFinder = resourceFinder;
    }

    @Override
    public void resolve(List<Th2Link> linkResources, List<EnqueuedLink> activeLinks,
                        Th2CustomResource... newResources) {

        var activeLinksCopy = new ArrayList<>(activeLinks);

        activeLinks.clear();

        for (var lRes : linkResources) {

            var namespace = ExtractUtils.extractNamespace(lRes);

            for (var pinCouple : lRes.getSpec().getBoxesRelation().getRouterMq()) {

                var queueBunch = new QueueDescription(
                        new QueueName(namespace, pinCouple.getTo()),
                        new RoutingKeyName(namespace, pinCouple.getFrom()),
                        RabbitMQContext.getExchangeName(namespace)
                );

                if (Arrays.stream(newResources)
                        .map(res -> res.getMetadata().getName())
                        .anyMatch(name -> name.equals(pinCouple.getFrom().getBoxName())
                                || name.equals(pinCouple.getTo().getBoxName()))) {
                    var resourceCouple = validateAndReturnRes(lRes, pinCouple, newResources);

                    if (resourceCouple == null) {
                        continue;
                    }

                    bindQueues(namespace, queueBunch, resourceCouple.to, pinCouple.getTo());
                }

                var alreadyExistLink = activeLinksCopy.stream()
                        .filter(l -> l.getQueueDescription().equals(queueBunch) && l.getPinCoupling().equals(pinCouple))
                        .findFirst()
                        .orElse(null);

                activeLinks.add(Objects.requireNonNullElseGet(alreadyExistLink,
                        () -> new EnqueuedLink(pinCouple, queueBunch)));
            }
        }

        if (linkResources.size() > 0) {
            removeExtinctQueue(ExtractUtils.extractNamespace(linkResources.get(0)), activeLinksCopy, activeLinks);
        }
    }

    @SneakyThrows
    private void bindQueues(String namespace, QueueDescription queue, Th2CustomResource resource, PinMQ mqPin) {

        try {
            Channel channel = RabbitMQContext.getChannel(namespace);

            if (!channel.isOpen()) {
                logger.warn("RabbitMQ connection is broken, trying to reconnect...");
                RabbitMQContext.closeChannel(namespace);
                channel = RabbitMQContext.getChannel(namespace);
                logger.info("RabbitMQ connection has been restored");
            }

            var pin = resource.getSpec().getPin(mqPin.getPinName());
            String queueName = queue.getQueueName().toString();
            var newQueueArguments = RabbitMQContext.generateQueueArguments(pin.getSettings());
            var currentQueue = RabbitMQContext.getQueue(namespace, queueName);
            if (currentQueue == null) {
                logger.info("Queue '{}' does not yet exist. skipping binding", queueName);
                return;
            }
            if (!currentQueue.getArguments().equals(newQueueArguments)) {
                logger.warn("Arguments for queue '{}' were modified. Recreating with new arguments", queueName);
                channel.queueDelete(queueName);
                channel.queueDeclare(queueName, rabbitMQManagementConfig.isPersistence(), false,
                        false, newQueueArguments);
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
    private void removeExtinctQueue(String namespace, List<EnqueuedLink> oldLinks, List<EnqueuedLink> newLinks) {

        oldLinks.removeIf(qlb -> newLinks.stream().anyMatch(newQlb ->
                qlb.getQueueDescription().getQueueName().equals(newQlb.getQueueDescription().getQueueName())
                        && qlb.getQueueDescription().getRoutingKey()
                        .equals(newQlb.getQueueDescription().getRoutingKey())
        ));
        try {
            Channel channel = RabbitMQContext.getChannel(namespace);

            for (var extinctLink : oldLinks) {

                var fromBox = extinctLink.getFrom();
                var toBox = extinctLink.getTo();
                QueueDescription queueBunch = extinctLink.getQueueDescription();
                var queueName = queueBunch.getQueueName().toString();
                var routingKey = queueBunch.getRoutingKey().toString();

                var currentQueue = RabbitMQContext.getQueue(namespace, queueName);
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

    private ResourceCouple validateAndReturnRes(Th2Link linkRes, PinCouplingMQ link,
                                                Th2CustomResource... additionalSource) {
        var namespace = ExtractUtils.extractNamespace(linkRes);

        if (!th2PinEndpointPreValidation(namespace,
                link.getFrom().getBoxName(),
                link.getTo().getBoxName())) {
            logger.warn("One of the boxes {} or {} is not present in the cache",
                    link.getFrom().getBoxName(), link.getTo().getBoxName());
            return null;
        }

        var fromBoxSpec = link.getFrom();

        var fromBoxName = fromBoxSpec.getBoxName();

        var fromContext = DirectionalLinkContext.builder()
                .linkName(link.getName())
                .boxName(fromBoxName)
                .boxPinName(fromBoxSpec.getPinName())
                .boxDirection(BoxDirection.from)
                .linksSectionName("mq")
                .connectionType(SchemaConnectionType.mq)
                .linkResName(ExtractUtils.extractName(linkRes))
                .linkNamespace(namespace)
                .build();

        var toBoxSpec = link.getTo();

        var toBoxName = toBoxSpec.getBoxName();

        var toContext = fromContext.toBuilder()
                .boxName(toBoxName)
                .boxPinName(toBoxSpec.getPinName())
                .boxDirection(BoxDirection.to)
                .build();

        var fromRes = resourceFinder.getResource(fromBoxName, namespace, additionalSource);

        var fromValRes = validateResourceByDirectionalLink(fromRes, fromContext);

        var toRes = resourceFinder.getResource(toBoxName, namespace, additionalSource);

        var toValRes = validateResourceByDirectionalLink(toRes, toContext);

        if (fromValRes.equals(ValidationStatus.VALID) && toValRes.equals(ValidationStatus.VALID)) {
            return new ResourceCouple(fromRes, toRes);
        }

        return null;
    }

    private ValidationStatus validateResourceByDirectionalLink(Th2CustomResource resource,
                                                               DirectionalLinkContext context) {
        var resValidator = new ResourceExist(context);
        var pinExist = new PinExist(context);
        var expectedPin = new ExpectedPinType(context);
        var expectedPublishPin = new ExpectedPinAttr(context);

        resValidator.setNext(pinExist);
        pinExist.setNext(expectedPin);
        expectedPin.setNext(expectedPublishPin);

        return resValidator.validate(resource);
    }

    @Getter
    @AllArgsConstructor
    private static class ResourceCouple {

        private final Th2CustomResource from;

        private final Th2CustomResource to;
    }
}
