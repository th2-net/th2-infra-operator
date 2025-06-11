/*
 * Copyright 2020-2025 Exactpro (Exactpro Systems Limited)
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
import com.exactpro.th2.infraoperator.util.Utils;
import com.rabbitmq.client.BuiltinExchangeType;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.spec.strategy.linkresolver.Util.createEstoreQueue;
import static com.exactpro.th2.infraoperator.spec.strategy.linkresolver.Util.createMstoreQueue;
import static com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.QueueName.QUEUE_NAME_REGEXP;
import static com.exactpro.th2.infraoperator.util.Strings.anyPrefixMatch;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;

public final class RabbitMQContext implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQContext.class);

    private static final List<String> USER_TAGS = List.of(
            // this tag is required for interact with RabbitMQ management https://www.rabbitmq.com/docs/management
            "monitoring"
    );

    private static final int RETRY_DELAY = 120;

    public static final String TOPIC = BuiltinExchangeType.TOPIC.getType();

    public static final String DIRECT = BuiltinExchangeType.DIRECT.getType();

    private final RabbitMQManagementConfig managementConfig;

    private final ChannelContext channelContext;

    private final Client rmqClient;

    private static final RetryableTaskQueue retryableTaskQueue = new RetryableTaskQueue();

    public RabbitMQContext(RabbitMQManagementConfig managementConfig) throws MalformedURLException, URISyntaxException {
        this.managementConfig = managementConfig;
        this.rmqClient = createClient(managementConfig);
        this.channelContext = new ChannelContext(this, managementConfig);
    }

    public String getTopicExchangeName() {
        return managementConfig.getExchangeName();
    }

    public void declareTopicExchange() {
        String exchangeName = getTopicExchangeName();
        try {
            getChannel().exchangeDeclare(exchangeName, TOPIC, managementConfig.getPersistence());
        } catch (Exception e) {
            LOGGER.error("Exception setting up exchange: \"{}\"", exchangeName, e);
            RetryTopicExchangeTask retryTopicExchangeTask = new RetryTopicExchangeTask(this, exchangeName, RETRY_DELAY);
            retryableTaskQueue.add(retryTopicExchangeTask, true);
            LOGGER.info("Task \"{}\" added to scheduler, with delay \"{}\" seconds",
                    retryTopicExchangeTask.getName(), RETRY_DELAY);
        }
    }

    public void setUpRabbitMqForNamespace(String namespace) {
        try {
            createUser(namespace);
            declareTopicExchange();
            declareExchange(toExchangeName(namespace));
            createStoreQueues(namespace);
        } catch (Exception e) {
            LOGGER.error("Exception setting up rabbitMq for namespace: \"{}\"", namespace, e);
            RetryRabbitSetup retryRabbitSetup = new RetryRabbitSetup(this, namespace, RETRY_DELAY);
            retryableTaskQueue.add(retryRabbitSetup, true);
            LOGGER.info("Task \"{}\" added to scheduler, with delay \"{}\" seconds",
                    retryRabbitSetup.getName(), RETRY_DELAY);
        }
    }

    private void createUser(String namespace) {

        RabbitMQConfig rabbitMQConfig = getRabbitMQConfig(namespace);

        String password = rabbitMQConfig.getPassword();
        String vHostName = managementConfig.getVhostName();

        if (StringUtils.isBlank(namespace)) {
            return;
        }

        try {
            if (rmqClient.getVhost(vHostName) == null) {
                LOGGER.error("vHost: \"{}\" is not present for add \"{}\" user", vHostName, namespace);
                return;
            }

            rmqClient.createUser(namespace, password.toCharArray(), USER_TAGS);
            LOGGER.info("Created user \"{}\" on vHost \"{}\" with tags {}", namespace, vHostName, USER_TAGS);

            // set permissions
            RabbitMQNamespacePermissions rabbitMQNamespacePermissions = managementConfig.getSchemaPermissions();
            UserPermissions permissions = new UserPermissions();
            permissions.setConfigure(rabbitMQNamespacePermissions.getConfigure());
            permissions.setRead(rabbitMQNamespacePermissions.getRead());
            permissions.setWrite(rabbitMQNamespacePermissions.getWrite());

            rmqClient.updatePermissions(vHostName, namespace, permissions);
            LOGGER.info("User \"{}\" permissions set in RabbitMQ", namespace);
        } catch (Exception e) {
            LOGGER.error("Exception setting up user: \"{}\" for vHost: \"{}\"", namespace, vHostName, e);
            throw e;
        }
    }

    private void declareExchange(String exchangeName) throws Exception {
        try {
            getChannel().exchangeDeclare(exchangeName, DIRECT, managementConfig.getPersistence());
        } catch (Exception e) {
            LOGGER.error("Exception setting up exchange: \"{}\"", exchangeName, e);
            throw e;
        }
    }

    private void createStoreQueues(String namespace) throws Exception {
        var channel = getChannel();
        var declareResult = channel.queueDeclare(
                createEstoreQueue(namespace),
                managementConfig.getPersistence(),
                false,
                false,
                null
        );
        LOGGER.info("Queue \"{}\" was successfully declared", declareResult.getQueue());
        declareResult = channel.queueDeclare(
                createMstoreQueue(namespace),
                managementConfig.getPersistence(),
                false,
                false,
                null
        );
        LOGGER.info("Queue \"{}\" was successfully declared", declareResult.getQueue());
    }

    public void cleanupRabbit(String namespace) {
        removeSchemaExchange(toExchangeName(namespace));
        removeSchemaQueues(namespace);
        removeSchemaUser(namespace);

    }

    private void removeSchemaUser(String namespace) {
        String vHostName = managementConfig.getVhostName();

        if (rmqClient.getVhost(vHostName) == null) {
            LOGGER.error("vHost: \"{}\" is not present for removing \"{}\" user", vHostName, namespace);
            return;
        }

        rmqClient.deleteUser(namespace);
        LOGGER.info("Deleted user \"{}\" from vHost \"{}\"", namespace, vHostName);

    }

    private void removeSchemaExchange(String exchangeName) {
        try {
            getChannel().exchangeDelete(exchangeName);
        } catch (Exception e) {
            LOGGER.error("Exception deleting exchange: \"{}\"", exchangeName, e);
        }
    }

    private void removeSchemaQueues(String namespace) {
        try {
            Channel channel = getChannel();

            List<QueueInfo> queueInfoList = getQueues();
            queueInfoList.forEach(q -> {
                String queueName = q.getName();
                var queue = QueueName.fromString(queueName);
                if (queue != null && queue.getNamespace().equals(namespace)) {
                    try {
                        channel.queueDelete(queueName);
                        LOGGER.info("Deleted queue: [{}]", queueName);
                    } catch (IOException e) {
                        LOGGER.error("Exception deleting queue: [{}]", queueName, e);
                    }
                }
            });
        } catch (Exception e) {
            LOGGER.error("Exception cleaning up queues for: \"{}\"", namespace, e);
        }
    }

    public static String toExchangeName(String namespace) {
        return namespace;
    }

    public Channel getChannel() {
        return channelContext.getChannel();
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

    public @NotNull List<QueueInfo> getQueues() {
        String vHostName = managementConfig.getVhostName();

        try {
            return requireNonNullElse(rmqClient.getQueues(vHostName), emptyList());
        } catch (Exception e) {
            String message = "Exception while fetching queues";
            LOGGER.error(message, e);
            throw new NonTerminalException(message, e);
        }
    }

    public @NotNull List<QueueInfo> getTh2Queues() {
        return getQueues().stream()
                .filter(queueInfo -> queueInfo.getName() != null && queueInfo.getName().matches(QUEUE_NAME_REGEXP))
                .collect(Collectors.toList());
    }

    public List<BindingInfo> getQueueBindings(String queue) {
        String vHostName = managementConfig.getVhostName();
        try {
            return rmqClient.getQueueBindings(vHostName, queue);
        } catch (Exception e) {
            String message = "Exception while fetching bindings";
            LOGGER.error(message, e);
            throw new NonTerminalException(message, e);
        }
    }

    public @NotNull List<ExchangeInfo> getExchanges() {
        try {
            return rmqClient.getExchanges();
        } catch (Exception e) {
            String message = "Exception while fetching exchanges";
            LOGGER.error(message, e);
            throw new NonTerminalException(message, e);
        }
    }

    public @NotNull List<ExchangeInfo> getTh2Exchanges() {
        Collection<String> namespacePrefixes = ConfigLoader.getConfig().getNamespacePrefixes();
        String topicExchange = getTopicExchangeName();
        return getExchanges().stream()
                .filter(exchangeInfo -> {
                    String name = exchangeInfo.getName();
                    return isNoneBlank(name)
                            && (name.equals(topicExchange) || anyPrefixMatch(name, namespacePrefixes));

                }).collect(Collectors.toList());
    }

    public QueueInfo getQueue(String queueName) {

        String vHostName = managementConfig.getVhostName();
        try {
            return rmqClient.getQueue(vHostName, queueName);
        } catch (Exception e) {
            String message = "Exception while fetching queue";
            LOGGER.error(message, e);
            throw new NonTerminalException(message, e);
        }
    }

    public static Client createClient(
        String host,
        int port,
        String username,
        String password
    ) throws MalformedURLException, URISyntaxException {
            return new Client(new ClientParameters()
                    .url(format("http://%s:%s/api", host, port))
                    .username(username)
                    .password(password)
            );
    }

    private static Client createClient(
        RabbitMQManagementConfig rabbitMQMngConfig
    ) throws MalformedURLException, URISyntaxException {
        return createClient(rabbitMQMngConfig.getHost(),
                rabbitMQMngConfig.getManagementPort(),
                rabbitMQMngConfig.getUsername(),
                rabbitMQMngConfig.getPassword());
    }

    private static RabbitMQConfig getRabbitMQConfig(String namespace) {

        RabbitMQConfig rabbitMQConfig = ConfigMaps.INSTANCE.getRabbitMQConfig4Namespace(namespace);
        if (rabbitMQConfig == null) {
            throw new NonTerminalException(format(
                    "RabbitMQ configuration for namespace \"%s\" is not available", namespace));
        }
        return rabbitMQConfig;
    }

    @Override
    public void close() {
        Utils.close(channelContext, "AMQP channel context");
    }

    static class ChannelContext implements AutoCloseable {

        private final Lock lock = new ReentrantLock();

        private final RabbitMQContext rabbitMQContext;

        private final RabbitMQManagementConfig rabbitMQManagementConfig;

        private Connection connection;

        private Channel channel;

        ChannelContext(RabbitMQContext rabbitMQContext, RabbitMQManagementConfig rabbitMQManagementConfig) {
            this.rabbitMQContext = rabbitMQContext;
            this.rabbitMQManagementConfig = rabbitMQManagementConfig;
            getChannel();
        }

        public Channel getChannel() {
            lock.lock();
            try {
                if (connection == null || !connection.isOpen()) {
                    close();
                    LOGGER.warn("RabbitMQ connection is broken, trying to reconnect...");
                    connection = createConnection();
                }
                if (channel == null || !channel.isOpen()) {
                    channel = connection.createChannel();
                }
                return channel;
            } catch (Exception e) {
                close();
                String message = "Exception while creating rabbitMq channel";
                LOGGER.error(message, e);
                throw new NonTerminalException(message, e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void close() {
            lock.lock();
            try {
                if (channel != null && channel.isOpen()) {
                    Utils.close(channel, "AMQP channel");
                }
                if (connection != null && connection.isOpen()) {
                    Utils.close(connection, "AMQP connection");
                }
                connection = null;
                channel = null;
            } finally {
                lock.unlock();
            }
        }

        private Connection createConnection() throws IOException, TimeoutException {
            ConnectionFactory connectionFactory = createConnectionFactory();
            Connection connection = connectionFactory.newConnection();
            connection.addShutdownListener(new RmqClientShutdownEventListener(rabbitMQContext));
            return connection;
        }

        @NotNull
        private ConnectionFactory createConnectionFactory() {
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
        private final RabbitMQContext rabbitMQContext;

        private RmqClientShutdownEventListener(RabbitMQContext rabbitMQContext) {
            this.rabbitMQContext = rabbitMQContext;
        }

        @Override
        public void shutdownCompleted(ShutdownSignalException cause) {
            LOGGER.error("Detected Rabbit mq connection lose", cause);
            RecreateQueuesAndBindings recreateQueuesAndBindingsTask = new RecreateQueuesAndBindings(
                    rabbitMQContext,
                    OperatorState.INSTANCE.getAllBoxResources(),
                    RETRY_DELAY
            );
            retryableTaskQueue.add(recreateQueuesAndBindingsTask, true);
            LOGGER.info("Task \"{}\" added to scheduler, with delay \"{}\" seconds",
                    recreateQueuesAndBindingsTask.getName(), RETRY_DELAY);
        }
    }
}
