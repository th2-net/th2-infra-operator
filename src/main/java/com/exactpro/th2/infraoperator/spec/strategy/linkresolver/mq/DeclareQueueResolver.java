/*
 * Copyright 2020-2024 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.configuration.ConfigLoader;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease;
import com.exactpro.th2.infraoperator.spec.shared.pin.MqSubscriberPin;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.QueueName;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.NonTerminalException;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import com.exactpro.th2.infraoperator.util.HelmReleaseUtils;
import com.rabbitmq.client.Channel;
import com.rabbitmq.http.client.domain.QueueInfo;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.exactpro.th2.infraoperator.spec.strategy.linkresolver.Util.buildEstoreQueue;
import static com.exactpro.th2.infraoperator.spec.strategy.linkresolver.Util.buildMstoreQueue;
import static com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext.getChannel;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNullElse;

public class DeclareQueueResolver {

    private static final Logger logger = LoggerFactory.getLogger(DeclareQueueResolver.class);

    public static void resolveAdd(Th2CustomResource resource) {
        String namespace = ExtractUtils.extractNamespace(resource);
        try {
            declareQueueBunch(namespace, resource);
        } catch (Exception e) {
            String message = "Exception while working with rabbitMq";
            logger.error(message, e);
            throw new NonTerminalException(message, e);
        }
    }

    public static void resolveDelete(Th2CustomResource resource) {
        String namespace = ExtractUtils.extractNamespace(resource);
        try {
            Channel channel = getChannel();
            //get queues that are associated with current box.
            Set<String> boxQueueNames = generateBoxQueues(namespace, resource);
            removeExtinctQueues(channel, boxQueueNames, CustomResourceUtils.annotationFor(resource), namespace);
        } catch (Exception e) {
            String message = "Exception while working with rabbitMq";
            logger.error(message, e);
            throw new NonTerminalException(message, e);
        }
    }

    private static void declareQueueBunch(String namespace, Th2CustomResource resource) throws IOException {

        Channel channel = getChannel();

        boolean persistence = ConfigLoader.getConfig().getRabbitMQManagement().getPersistence();
        //get queues that are associated with current box and are not linked through Th2Link resources
        Set<String> boxQueues = getBoxPreviousQueues(namespace, extractName(resource));

        for (var pin : resource.getSpec().getPins().getMq().getSubscribers()) {
            String boxName = extractName(resource);

            String queueName = new QueueName(namespace, boxName, pin.getName()).toString();
            //remove from set if pin for queue still exists.
            boxQueues.remove(queueName);
            var newQueueArguments = RabbitMQContext.generateQueueArguments(pin.getSettings());
            var currentQueue = RabbitMQContext.getQueue(queueName);
            if (currentQueue != null && !currentQueue.getArguments().equals(newQueueArguments)) {
                logger.warn("Arguments for queue '{}' were modified. Recreating with new arguments", queueName);
                channel.queueDelete(queueName);
            }
            var declareResult = channel.queueDeclare(queueName
                    , persistence
                    , false
                    , false
                    , newQueueArguments);
            logger.info("Queue '{}' of resource {} was successfully declared",
                    declareResult.getQueue(), extractName(resource));
        }
        //remove from rabbit queues that are left i.e. inactive
        removeExtinctQueues(channel, boxQueues, CustomResourceUtils.annotationFor(resource), namespace);
    }

    private static Set<String> getBoxPreviousQueues(String namespace, String boxName) {
        HelmRelease hr = OperatorState.INSTANCE.getHelmReleaseFromCache(boxName, namespace);
        if (hr == null) {
            return getBoxQueuesFromRabbit(namespace, boxName);
        }
        return HelmReleaseUtils.extractQueues(hr.getComponentValuesSection());
    }

    /**
     * Collect all queues related to the {@code namespace} {@code boxName} component in RabbitMQ
     * @return mutable set of queues
    */
    private static @NotNull Set<String> getBoxQueuesFromRabbit(String namespace, String boxName) {

        List<QueueInfo> queueInfoList = requireNonNullElse(RabbitMQContext.getQueues(), emptyList());

        Set<String> queueNames = new HashSet<>();
        queueInfoList.forEach(q -> {
            var queue = QueueName.fromString(q.getName());
            if (queue != null && queue.getBoxName().equals(boxName) && queue.getNamespace().equals(namespace)) {
                queueNames.add(q.getName());
            }
        });
        return queueNames;
    }

    private static Set<String> generateBoxQueues(String namespace, Th2CustomResource resource) {
        Set<String> queueNames = new HashSet<>();
        String boxName = ExtractUtils.extractName(resource);
        for (MqSubscriberPin mqPin : resource.getSpec().getPins().getMq().getSubscribers()) {
            queueNames.add(new QueueName(namespace, boxName, mqPin.getName()).toString());
        }
        return queueNames;
    }

    private static void removeExtinctQueues(
            Channel channel,
            Set<String> extinctQueueNames,
            String resourceLabel,
            String namespace
    ) {
        String estoreQueue = buildEstoreQueue(namespace);
        String mstoreQueue = buildMstoreQueue(namespace);

        if (!extinctQueueNames.isEmpty()) {
            logger.info("Trying to delete queues associated with \"{}\"", resourceLabel);
            extinctQueueNames
                    .stream()
                    .filter(name -> !name.equals(estoreQueue) && !name.equals(mstoreQueue))
                    .forEach(queueName -> {
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
