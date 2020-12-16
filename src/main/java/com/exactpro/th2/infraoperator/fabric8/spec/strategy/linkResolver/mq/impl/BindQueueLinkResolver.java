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
import com.exactpro.th2.infraoperator.fabric8.configuration.RabbitMQManagementConfig;
import com.exactpro.th2.infraoperator.fabric8.model.box.schema.link.QueueBunch;
import com.exactpro.th2.infraoperator.fabric8.model.box.schema.link.QueueLinkBunch;
import com.exactpro.th2.infraoperator.fabric8.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.fabric8.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.boxes.box.impl.BoxMq;
import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.boxes.bunch.impl.MqLinkBunch;
import com.exactpro.th2.infraoperator.fabric8.spec.link.validator.ValidationStatus;
import com.exactpro.th2.infraoperator.fabric8.spec.link.validator.chain.impl.ExpectedPinAttr;
import com.exactpro.th2.infraoperator.fabric8.spec.link.validator.chain.impl.ExpectedPinType;
import com.exactpro.th2.infraoperator.fabric8.spec.link.validator.chain.impl.PinExist;
import com.exactpro.th2.infraoperator.fabric8.spec.link.validator.chain.impl.ResourceExist;
import com.exactpro.th2.infraoperator.fabric8.spec.link.validator.model.DirectionalLinkContext;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.BoxDirection;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.PinSettings;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.SchemaConnectionType;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.Queue;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.mq.QueueLinkResolver;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.resFinder.box.BoxResourceFinder;
import com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.http.client.domain.QueueInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.mq.impl.RabbitMqStaticContext.generateQueueArguments;


public class BindQueueLinkResolver implements QueueLinkResolver {

    private static final Logger logger = LoggerFactory.getLogger(BindQueueLinkResolver.class);

    private final ConnectionFactory connectionFactory;

    private final BoxResourceFinder resourceFinder;

    private final RabbitMQManagementConfig rabbitMQManagementConfig;

    @SneakyThrows
    public BindQueueLinkResolver(BoxResourceFinder resourceFinder) {
        this.rabbitMQManagementConfig = OperatorConfig.INSTANCE.getRabbitMQManagementConfig();
        this.resourceFinder = resourceFinder;
        this.connectionFactory = new ConnectionFactory();
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
    private void bindQueues(String namespace, QueueBunch queueBunch, Th2CustomResource resource, BoxMq boxMq) {

        Map<String, RabbitMqStaticContext.ChannelBunch> channelBunchMap = RabbitMqStaticContext.getMqChannels();

        Channel channel = channelBunchMap.get(namespace).getChannel();

        if (!channel.isOpen()) {
            logger.warn("RMQ connection is broken, trying to reconnect...");
            channelBunchMap.remove(namespace);
            RabbitMqStaticContext.createChannelIfAbsent(namespace, rabbitMQManagementConfig, connectionFactory);
            channel = channelBunchMap.get(namespace).getChannel();
            logger.info("RMQ connection has been restored");
        }

        PinSettings pinSettings = resource.getSpec().getPin(boxMq.getPin()).getSettings();
        channel.queueDeclare(queueBunch.getQueue(), rabbitMQManagementConfig.isPersistence(), false, false, generateQueueArguments(pinSettings));
        channel.queueBind(queueBunch.getQueue(), queueBunch.getExchange(), queueBunch.getRoutingKey());

    }

    @SneakyThrows
    private void removeExtinctQueue(String namespace, List<QueueLinkBunch> oldLinks, List<QueueLinkBunch> newLinks) {

        oldLinks.removeIf(qlb -> newLinks.stream().anyMatch(newQlb ->
                qlb.getQueueBunch().getQueue().equals(newQlb.getQueueBunch().getQueue())
                        && qlb.getQueueBunch().getRoutingKey().equals(newQlb.getQueueBunch().getRoutingKey())
        ));

        for (var extinctLink : oldLinks) {

            var fromBox = extinctLink.getFrom();

            var toBox = extinctLink.getTo();

            var queueBunch = extinctLink.getQueueBunch();

            var queue = queueBunch.getQueue();

            var routingKey = queueBunch.getRoutingKey();

            var channel = RabbitMqStaticContext.getMqChannels().get(namespace).getChannel();

            channel.queueUnbind(queue, queueBunch.getExchange(), routingKey);

            var infoMsg = String.format(
                    "Unbind queue '%1$s' -> '%2$s' because link {%5$s.%3$s -> %5$s.%4$s} is not active anymore",
                    queue, routingKey, fromBox, toBox, namespace
            );

            int msgCount = 0;

            if (!isQueueUsed(queueBunch, newLinks)) {
                msgCount = channel.queueDelete(queue).getMessageCount();
                infoMsg += ". Queue has been deleted because it's not bind for any routing key";
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

    private QueueBunch createQueueBunch(String namespace, BoxMq fromBoxMq, BoxMq toBoxMq) {

        var rabbitMQConfig = RabbitMqStaticContext.getRabbitMQConfig(namespace);
        return new QueueBunch(
                new Queue(toBoxMq, namespace).toQueueString(),
                new Queue(fromBoxMq, namespace).toRoutingKeyString(),
                rabbitMQConfig.getExchangeName()
        );
    }

    private ResourceCouple validateAndReturnRes(Th2Link linkRes, MqLinkBunch link, Th2CustomResource... additionalSource) {

        var namespace = ExtractUtils.extractNamespace(linkRes);


        var fromBoxSpec = link.getFrom();

        var fromBoxName = fromBoxSpec.getBox();

        var fromContext = DirectionalLinkContext.builder()
                .linkName(link.getName())
                .boxName(fromBoxName)
                .boxPinName(fromBoxSpec.getPin())
                .boxDirection(BoxDirection.from)
                .linksSectionName("mq")
                .connectionType(SchemaConnectionType.mq)
                .linkResName(ExtractUtils.extractName(linkRes))
                .linkNamespace(namespace)
                .build();


        var toBoxSpec = link.getTo();

        var toBoxName = toBoxSpec.getBox();

        var toContext = fromContext.toBuilder()
                .boxName(toBoxName)
                .boxPinName(toBoxSpec.getPin())
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
