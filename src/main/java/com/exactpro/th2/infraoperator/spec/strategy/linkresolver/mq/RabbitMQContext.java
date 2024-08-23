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
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQConfig;
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQManagementConfig;
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQNamespacePermissions;
import com.exactpro.th2.infraoperator.model.kubernetes.configmaps.ConfigMaps;
import com.exactpro.th2.infraoperator.spec.shared.PinSettings;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.QueueName;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.NonTerminalException;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.RetryableTaskQueue;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.tasks.RecreateQueuesAndBindings;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.tasks.RetryRabbitSetup;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.tasks.RetryTopicExchangeTask;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.http.client.Client;
import com.rabbitmq.http.client.ClientParameters;
import com.rabbitmq.http.client.domain.BindingInfo;
import com.rabbitmq.http.client.domain.ExchangeInfo;
import com.rabbitmq.http.client.domain.QueueInfo;
import com.rabbitmq.http.client.domain.UserPermissions;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.spec.strategy.linkresolver.Util.createEstoreQueue;
import static com.exactpro.th2.infraoperator.spec.strategy.linkresolver.Util.createMstoreQueue;
import static com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.QueueName.QUEUE_NAME_REGEXP;
import static com.exactpro.th2.infraoperator.util.Strings.anyPrefixMatch;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;

public final class RabbitMQContext {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQContext.class);

    private static final int RETRY_DELAY = 120;
    public static final String TOPIC = "topic";
    public static final String DIRECT = "direct";

    private static volatile RabbitMQManagementConfig managementConfig;

    private static volatile ChannelContext channelContext;

    private static volatile Client rmqClient;

    private static final RetryableTaskQueue retryableTaskQueue = new RetryableTaskQueue();

    private RabbitMQContext() {
    }

    public static String getTopicExchangeName() {
        return getManagementConfig().getExchangeName();
    }

    public static void declareTopicExchange() {
        String exchangeName = getTopicExchangeName();
        RabbitMQManagementConfig rabbitMQManagementConfig = getManagementConfig();
        try {
            getChannel().exchangeDeclare(exchangeName, TOPIC, rabbitMQManagementConfig.getPersistence());
        } catch (Exception e) {
            logger.error("Exception setting up exchange: \"{}\"", exchangeName, e);
            RetryTopicExchangeTask retryTopicExchangeTask = new RetryTopicExchangeTask(exchangeName, RETRY_DELAY);
            retryableTaskQueue.add(retryTopicExchangeTask, true);
            logger.info("Task \"{}\" added to scheduler, with delay \"{}\" seconds",
                    retryTopicExchangeTask.getName(), RETRY_DELAY);
        }
    }

    public static void setUpRabbitMqForNamespace(String namespace) {
        try {
            createUser(namespace);
            declareExchange(toExchangeName(namespace));
            createStoreQueues(namespace);
        } catch (Exception e) {
            logger.error("Exception setting up rabbitMq for namespace: \"{}\"", namespace, e);
            RetryRabbitSetup retryRabbitSetup = new RetryRabbitSetup(namespace, RETRY_DELAY);
            retryableTaskQueue.add(retryRabbitSetup, true);
            logger.info("Task \"{}\" added to scheduler, with delay \"{}\" seconds",
                    retryRabbitSetup.getName(), RETRY_DELAY);
        }
    }

    private static void createUser(String namespace) throws Exception {

        RabbitMQManagementConfig rabbitMQManagementConfig = getManagementConfig();
        RabbitMQConfig rabbitMQConfig = getRabbitMQConfig(namespace);

        String password = rabbitMQConfig.getPassword();
        String vHostName = rabbitMQManagementConfig.getVhostName();

        if (StringUtils.isBlank(namespace)) {
            return;
        }

        try {
            Client rmqClient = getClient();

            if (rmqClient.getVhost(vHostName) == null) {
                logger.error("vHost: \"{}\" is not present", vHostName);
                return;
            }

            rmqClient.createUser(namespace, password.toCharArray(), new ArrayList<>());
            logger.info("Created user \"{}\" on vHost \"{}\"", namespace, vHostName);

            // set permissions
            RabbitMQNamespacePermissions rabbitMQNamespacePermissions =
                    rabbitMQManagementConfig.getSchemaPermissions();
            UserPermissions permissions = new UserPermissions();
            permissions.setConfigure(rabbitMQNamespacePermissions.getConfigure());
            permissions.setRead(rabbitMQNamespacePermissions.getRead());
            permissions.setWrite(rabbitMQNamespacePermissions.getWrite());

            rmqClient.updatePermissions(vHostName, namespace, permissions);
            logger.info("User \"{}\" permissions set in RabbitMQ", namespace);
        } catch (Exception e) {
            logger.error("Exception setting up user: \"{}\" for vHost: \"{}\"", namespace, vHostName, e);
            throw e;
        }
    }

    private static void declareExchange(String exchangeName) throws Exception {
        RabbitMQManagementConfig rabbitMQManagementConfig = getManagementConfig();
        try {
            getChannel().exchangeDeclare(exchangeName, DIRECT, rabbitMQManagementConfig.getPersistence());
        } catch (Exception e) {
            logger.error("Exception setting up exchange: \"{}\"", exchangeName, e);
            throw e;
        }
    }

    private static void createStoreQueues(String namespace) throws Exception {
        var channel = getChannel();
        RabbitMQManagementConfig rabbitMQManagementConfig = getManagementConfig();
        var declareResult = channel.queueDeclare(
                createEstoreQueue(namespace),
                rabbitMQManagementConfig.getPersistence(),
                false,
                false,
                null
        );
        logger.info("Queue \"{}\" was successfully declared", declareResult.getQueue());
        declareResult = channel.queueDeclare(
                createMstoreQueue(namespace),
                rabbitMQManagementConfig.getPersistence(),
                false,
                false,
                null
        );
        logger.info("Queue \"{}\" was successfully declared", declareResult.getQueue());
    }

    public static void cleanupRabbit(String namespace) throws Exception {
        removeSchemaExchange(toExchangeName(namespace));
        removeSchemaQueues(namespace);
        removeSchemaUser(namespace);

    }

    private static void removeSchemaUser(String namespace) throws Exception {
        RabbitMQManagementConfig rabbitMQManagementConfig = getManagementConfig();

        String vHostName = rabbitMQManagementConfig.getVhostName();

        Client rmqClient = getClient();

        if (rmqClient.getVhost(vHostName) == null) {
            logger.error("vHost: \"{}\" is not present", vHostName);
            return;
        }

        rmqClient.deleteUser(namespace);
        logger.info("Deleted user \"{}\" from vHost \"{}\"", namespace, vHostName);

    }

    private static void removeSchemaExchange(String exchangeName) {
        try {
            getChannel().exchangeDelete(exchangeName);
        } catch (Exception e) {
            logger.error("Exception deleting exchange: \"{}\"", exchangeName, e);
        }
    }

    private static void removeSchemaQueues(String namespace) {
        try {
            Channel channel = getChannel();

            List<QueueInfo> queueInfoList = getQueues();
            queueInfoList.forEach(q -> {
                String queueName = q.getName();
                var queue = QueueName.fromString(queueName);
                if (queue != null && queue.getNamespace().equals(namespace)) {
                    try {
                        channel.queueDelete(queueName);
                        logger.info("Deleted queue: [{}]", queueName);
                    } catch (IOException e) {
                        logger.error("Exception deleting queue: [{}]", queueName, e);
                    }
                }
            });
        } catch (Exception e) {
            logger.error("Exception cleaning up queues for: \"{}\"", namespace, e);
        }
    }

    public static String toExchangeName(String namespace) {
        return namespace;
    }

    public static Channel getChannel() {
        Channel channel = getChannelContext().channel;
        if (!channel.isOpen()) {
            logger.warn("RabbitMQ connection is broken, trying to reconnect...");
            getChannelContext().close();
            channel = getChannelContext().channel;
            logger.info("RabbitMQ connection has been restored");
        }
        return channel;
    }

    public static Map<String, Object> generateQueueArguments(PinSettings pinSettings) throws NumberFormatException {
        if (pinSettings == null) {
            return Collections.emptyMap();
        }
        if (pinSettings.getStorageOnDemand()) {
            return Collections.emptyMap();
        } else {
            Map<String, Object> args = new HashMap<>();
            int queueLength = pinSettings.getQueueLength();
            args.put("x-max-length", queueLength);
            args.put("x-overflow", pinSettings.getOverloadStrategy());
            return args;
        }
    }

    public static @NotNull List<QueueInfo> getQueues() {
        String vHostName = getManagementConfig().getVhostName();

        try {
            Client rmqClient = getClient();
            return requireNonNullElse(rmqClient.getQueues(vHostName), emptyList());
        } catch (Exception e) {
            String message = "Exception while fetching queues";
            logger.error(message, e);
            throw new NonTerminalException(message, e);
        }
    }

    public static @NotNull List<QueueInfo> getTh2Queues() {
        return getQueues().stream()
                .filter(queueInfo -> queueInfo.getName() != null && queueInfo.getName().matches(QUEUE_NAME_REGEXP))
                .collect(Collectors.toList());
    }

    public static List<BindingInfo> getQueueBindings(String queue) {
        String vHostName = getManagementConfig().getVhostName();
        try {
            Client rmqClient = getClient();
            return rmqClient.getQueueBindings(vHostName, queue);
        } catch (Exception e) {
            String message = "Exception while fetching bindings";
            logger.error(message, e);
            throw new NonTerminalException(message, e);
        }
    }

    public static @NotNull List<ExchangeInfo> getExchanges() {
        try {
            Client rmqClient = getClient();
            return rmqClient.getExchanges();
        } catch (Exception e) {
            String message = "Exception while fetching exchanges";
            logger.error(message, e);
            throw new NonTerminalException(message, e);
        }
    }

    public static @NotNull List<ExchangeInfo> getTh2Exchanges() {
        Collection<String> namespacePrefixes = ConfigLoader.getConfig().getNamespacePrefixes();
        String topicExchange = getTopicExchangeName();
        return getExchanges().stream()
                .filter(exchangeInfo -> {
                    String name = exchangeInfo.getName();
                    return isNoneBlank(name)
                            && (name.equals(topicExchange) || anyPrefixMatch(name, namespacePrefixes));

                }).collect(Collectors.toList());
    }

    public static QueueInfo getQueue(String queueName) {

        String vHostName = getManagementConfig().getVhostName();
        try {
            Client rmqClient = getClient();
            return rmqClient.getQueue(vHostName, queueName);
        } catch (Exception e) {
            String message = "Exception while fetching queue";
            logger.error(message, e);
            throw new NonTerminalException(message, e);
        }
    }

    public static Client createClient(String host, int port, String username, String password) throws MalformedURLException, URISyntaxException {
            return new Client(new ClientParameters()
                    .url(format("http://%s:%s/api", host, port))
                    .username(username)
                    .password(password)
            );
    }

    private static RabbitMQManagementConfig getManagementConfig() {
        // we do not need to synchronize as we are assigning immutable object from singleton
        if (managementConfig == null) {
            managementConfig = ConfigLoader.getConfig().getRabbitMQManagement();
        }
        return managementConfig;
    }

    private static Client getClient() throws MalformedURLException, URISyntaxException {
        if (rmqClient == null) {
            RabbitMQManagementConfig rabbitMQMngConfig = getManagementConfig();
            rmqClient = createClient(rabbitMQMngConfig.getHost(),
                    rabbitMQMngConfig.getManagementPort(),
                    rabbitMQMngConfig.getUsername(),
                    rabbitMQMngConfig.getPassword());
        }
        return rmqClient;
    }

    private static ChannelContext getChannelContext() {
        // we do not need to synchronize as we are assigning immutable object from singleton
        if (channelContext == null) {
            channelContext = new ChannelContext();
        }
        return channelContext;
    }

    private static RabbitMQConfig getRabbitMQConfig(String namespace) {

        RabbitMQConfig rabbitMQConfig = ConfigMaps.INSTANCE.getRabbitMQConfig4Namespace(namespace);
        if (rabbitMQConfig == null) {
            throw new NonTerminalException(format(
                    "RabbitMQ configuration for namespace \"%s\" is not available", namespace));
        }
        return rabbitMQConfig;
    }

    static class ChannelContext {

        private Connection connection;

        private Channel channel;

        ChannelContext() {
            ConnectionFactory connectionFactory = createConnectionFactory();
            try {
                this.connection = connectionFactory.newConnection();
                this.connection.addShutdownListener(new RmqClientShutdownEventListener());
                this.channel = connection.createChannel();
            } catch (Exception e) {
                close();
                String message = "Exception while creating rabbitMq channel";
                logger.error(message, e);
                throw new NonTerminalException(message, e);
            }
        }

        synchronized void close() {
            try {
                if (channel != null && channel.isOpen()) {
                    channel.close();
                }
            } catch (Exception e) {
                logger.error("Exception closing RabbitMQ channel", e);
            }
            try {
                if (connection != null && connection.isOpen()) {
                    connection.close();
                }
            } catch (Exception e) {
                logger.error("Exception closing RabbitMQ connection for", e);
            }
            channel = null;
            connection = null;
            channelContext = null;
        }

        @NotNull
        private static ConnectionFactory createConnectionFactory() {
            RabbitMQManagementConfig rabbitMQManagementConfig = getManagementConfig();
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setHost(rabbitMQManagementConfig.getHost());
            connectionFactory.setPort(rabbitMQManagementConfig.getApplicationPort());
            connectionFactory.setVirtualHost(rabbitMQManagementConfig.getVhostName());
            connectionFactory.setUsername(rabbitMQManagementConfig.getUsername());
            connectionFactory.setPassword(rabbitMQManagementConfig.getPassword());
            return connectionFactory;
        }
    }

    private static class RmqClientShutdownEventListener implements ShutdownListener {

        @Override
        public void shutdownCompleted(ShutdownSignalException cause) {
            logger.error("Detected Rabbit mq connection lose", cause);
            RecreateQueuesAndBindings recreateQueuesAndBindingsTask = new RecreateQueuesAndBindings(
                    OperatorState.INSTANCE.getAllBoxResources(),
                    RETRY_DELAY
            );
            retryableTaskQueue.add(recreateQueuesAndBindingsTask, true);
            logger.info("Task \"{}\" added to scheduler, with delay \"{}\" seconds",
                    recreateQueuesAndBindingsTask.getName(), RETRY_DELAY);
        }
    }
}
