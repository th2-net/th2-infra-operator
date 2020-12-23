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

package com.exactpro.th2.infraoperator.spec.strategy.linkResolver.mq.impl;

import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.configuration.RabbitMQManagementConfig;
import com.exactpro.th2.infraoperator.model.box.schema.link.QueueBunch;
import com.exactpro.th2.infraoperator.model.box.schema.link.QueueLinkBunch;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinMQ;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingMQ;
import com.exactpro.th2.infraoperator.spec.link.validator.ValidationStatus;
import com.exactpro.th2.infraoperator.spec.link.validator.chain.impl.ExpectedPinAttr;
import com.exactpro.th2.infraoperator.spec.link.validator.chain.impl.ExpectedPinType;
import com.exactpro.th2.infraoperator.spec.link.validator.chain.impl.PinExist;
import com.exactpro.th2.infraoperator.spec.link.validator.chain.impl.ResourceExist;
import com.exactpro.th2.infraoperator.spec.link.validator.model.DirectionalLinkContext;
import com.exactpro.th2.infraoperator.spec.shared.BoxDirection;
import com.exactpro.th2.infraoperator.spec.shared.PinSettings;
import com.exactpro.th2.infraoperator.spec.shared.SchemaConnectionType;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.mq.QueueLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.queue.QueueName;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.queue.RoutingKeyName;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.box.BoxResourceFinder;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import com.rabbitmq.client.Channel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class BindQueueLinkResolver implements QueueLinkResolver {

    private static final Logger logger = LoggerFactory.getLogger(BindQueueLinkResolver.class);
    private final BoxResourceFinder resourceFinder;
    private final RabbitMQManagementConfig rabbitMQManagementConfig;

    @SneakyThrows
    public BindQueueLinkResolver(BoxResourceFinder resourceFinder) {
        this.rabbitMQManagementConfig = OperatorConfig.INSTANCE.getRabbitMQManagementConfig();
        this.resourceFinder = resourceFinder;
    }


    @Override
    public List<QueueLinkBunch> resolve(List<Th2Link> linkResources) {

        List<QueueLinkBunch> qAssignedLinks = new ArrayList<>();

        resolve(linkResources, qAssignedLinks);

        return qAssignedLinks;
    }

    @Override
    public void resolve(List<Th2Link> linkResources, List<QueueLinkBunch> activeLinks) {
        resolve(linkResources, activeLinks, new Th2CustomResource[]{});
    }

    @Override
    public void resolve(List<Th2Link> linkResources, List<QueueLinkBunch> activeLinks, Th2CustomResource... newResources) {

        var activeLinksCopy = new ArrayList<>(activeLinks);

        activeLinks.clear();

        for (var lRes : linkResources) {

            var linkNamespace = ExtractUtils.extractNamespace(lRes);

            for (var link : lRes.getSpec().getBoxesRelation().getRouterMq()) {

                var resourceCouple = validateAndReturnRes(lRes, link, newResources);

                var queueBunch = createQueueBunch(linkNamespace, link.getFrom(), link.getTo());

                if (resourceCouple == null) {
                    continue;
                }

                bindQueues(linkNamespace, queueBunch, resourceCouple.to, link.getTo());

                logger.info("Queue '{}' of link {{}.{} -> {}.{}} successfully bound",
                        queueBunch.getQueue(), linkNamespace, link.getFrom(), linkNamespace, link.getTo());

                var alreadyExistLink = activeLinksCopy.stream()
                        .filter(l -> l.getQueueBunch().equals(queueBunch) && l.getMqLinkBunch().equals(link))
                        .findFirst()
                        .orElse(null);

                activeLinks.add(Objects.requireNonNullElseGet(alreadyExistLink, () -> new QueueLinkBunch(link, queueBunch)));

            }
        }

        if (linkResources.size() > 0) {
            removeExtinctQueue(ExtractUtils.extractNamespace(linkResources.get(0)), activeLinksCopy, activeLinks);
        }

    }


    @SneakyThrows
    private void bindQueues(String namespace, QueueBunch queueBunch, Th2CustomResource resource, PinMQ mqPin) {


        Channel channel = RabbitMQContext.getChannel(namespace);

        if (!channel.isOpen()) {
            logger.warn("RabbitMQ connection is broken, trying to reconnect...");
            RabbitMQContext.closeChannel(namespace);
            channel = RabbitMQContext.getChannel(namespace);
            logger.info("RabbitMQ connection has been restored");
        }

        PinSettings pinSettings = resource.getSpec().getPin(mqPin.getPinName()).getSettings();
        channel.queueDeclare(queueBunch.getQueue(), rabbitMQManagementConfig.isPersistence(), false, false, RabbitMQContext.generateQueueArguments(pinSettings));
        channel.queueBind(queueBunch.getQueue(), queueBunch.getExchange(), queueBunch.getRoutingKey());

    }

    @SneakyThrows
    private void removeExtinctQueue(String namespace, List<QueueLinkBunch> oldLinks, List<QueueLinkBunch> newLinks) {

        oldLinks.removeIf(qlb -> newLinks.stream().anyMatch(newQlb ->
                qlb.getQueueBunch().getQueue().equals(newQlb.getQueueBunch().getQueue())
                        && qlb.getQueueBunch().getRoutingKey().equals(newQlb.getQueueBunch().getRoutingKey())
        ));

        Channel channel = RabbitMQContext.getChannel(namespace);

        for (var extinctLink : oldLinks) {

            var fromBox = extinctLink.getFrom();
            var toBox = extinctLink.getTo();
            var queueBunch = extinctLink.getQueueBunch();
            var queue = queueBunch.getQueue();
            var routingKey = queueBunch.getRoutingKey();

            channel.queueUnbind(queue, queueBunch.getExchange(), routingKey);

            var infoMsg = String.format(
                    "Unbind queue '%1$s' -> '%2$s' because link {%5$s.%3$s -> %5$s.%4$s} is not active anymore",
                    queue, routingKey, fromBox, toBox, namespace
            );

            int msgCount = 0;

            if (!isQueueUsed(queueBunch, newLinks)) {
                msgCount = channel.queueDelete(queue).getMessageCount();
                infoMsg += ". Queue has been deleted because it's not bound for any routing key";
            }

            if (msgCount == 0) {
                logger.info(infoMsg);
            } else {
                logger.warn("{}. The queue contained {} messages, which are now lost!", infoMsg, msgCount);
            }
        }

    }

    private boolean isQueueUsed(QueueBunch targetQB, List<QueueLinkBunch> links) {
        return links.stream().anyMatch(qlb -> {
            var qb = qlb.getQueueBunch();
            return qb.getQueue().equals(targetQB.getQueue()) && qb.getExchange().equals(targetQB.getExchange());
        });
    }

    private QueueBunch createQueueBunch(String namespace, PinMQ mqPinFrom, PinMQ mqPinTo) {

        return new QueueBunch(
                new QueueName(namespace, mqPinTo).toString(),
                new RoutingKeyName(namespace, mqPinFrom).toString(),
                RabbitMQContext.getExchangeName(namespace)
        );

    }

    private ResourceCouple validateAndReturnRes(Th2Link linkRes, PinCouplingMQ link, Th2CustomResource... additionalSource) {

        var namespace = ExtractUtils.extractNamespace(linkRes);


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

    private ValidationStatus validateResourceByDirectionalLink(Th2CustomResource resource, DirectionalLinkContext context) {

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