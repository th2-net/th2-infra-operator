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

import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.configuration.RabbitMQConfig;
import com.exactpro.th2.infraoperator.configuration.RabbitMQManagementConfig;
import com.exactpro.th2.infraoperator.configuration.RabbitMQNamespacePermissions;
import com.exactpro.th2.infraoperator.model.kubernetes.configmaps.ConfigMaps;
import com.exactpro.th2.infraoperator.spec.shared.PinSettings;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.NonTerminalException;
import com.exactpro.th2.infraoperator.util.Strings;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.http.client.Client;
import com.rabbitmq.http.client.ClientParameters;
import com.rabbitmq.http.client.domain.QueueInfo;
import com.rabbitmq.http.client.domain.UserPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.lang.String.format;

public final class RabbitMQContext {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQContext.class);

    private static volatile RabbitMQManagementConfig managementConfig;

    private static volatile ChannelContext channelContext;

    private static volatile Client rmqClient;

    private RabbitMQContext() {
    }

    public static void declareTopicExchange() throws Exception {
        declareExchange(getManagementConfig().getExchangeName(), "topic");
    }

    private static void declareExchange(String exchangeName, String type) throws Exception {
        RabbitMQManagementConfig rabbitMQManagementConfig = getManagementConfig();
        String vHostName = rabbitMQManagementConfig.getVHostName();

        try {
            Client rmqClient = getClient();

            // check vhost
            if (rmqClient.getVhost(vHostName) == null) {
                logger.error("vHost: \"{}\" is not present", vHostName);
                return;
            }

            Channel channel = getChannel();

            // check channel
            if (!channel.isOpen()) {
                logger.warn("RabbitMQ connection is broken, trying to reconnect...");
                closeChannel();
                channel = getChannel();
                logger.info("RabbitMQ connection has been restored");
            }

            channel.exchangeDeclare(exchangeName, type, rabbitMQManagementConfig.isPersistence());

        } catch (Exception e) {
            logger.error("Exception setting up exchange: \"{}\" on vHost: \"{}\"", exchangeName, vHostName, e);
            throw e;
        }
    }

    public static void createUser(String namespace) {

        RabbitMQManagementConfig rabbitMQManagementConfig = getManagementConfig();
        RabbitMQConfig rabbitMQConfig = getRabbitMQConfig(namespace);

        String username = rabbitMQConfig.getUsername();
        String password = rabbitMQConfig.getPassword();
        String vHostName = rabbitMQManagementConfig.getVHostName();

        if (Strings.isNullOrEmpty(username)) {
            return;
        }

        try {
            Client rmqClient = getClient();

            if (rmqClient.getVhost(vHostName) == null) {
                logger.error("vHost: \"{}\" is not present", vHostName);
                return;
            }

            rmqClient.createUser(username, password.toCharArray(), new ArrayList<>());
            logger.info("Created user \"{}\" for vHost \"{}\"", username, vHostName);

            // set permissions
            RabbitMQNamespacePermissions rabbitMQNamespacePermissions =
                    rabbitMQManagementConfig.getRabbitMQNamespacePermissions();
            UserPermissions permissions = new UserPermissions();
            permissions.setConfigure(rabbitMQNamespacePermissions.getConfigure());
            permissions.setRead(rabbitMQNamespacePermissions.getRead());
            permissions.setWrite(rabbitMQNamespacePermissions.getWrite());

            rmqClient.updatePermissions(vHostName, username, permissions);
            logger.info("User \"{}\" permissions set in RabbitMQ", username);
        } catch (Exception e) {
            String message = format("Exception setting up user: \"%s\" for vHost: \"%s\"", username, vHostName);
            logger.error(message, e);
            throw new NonTerminalException(message, e);
        }
    }

    public static void cleanupSchemaExchange(String exchangeName) throws Exception {

        String vHostName = getManagementConfig().getVHostName();

        try {
            Client rmqClient = getClient();

            // check vhost
            if (rmqClient.getVhost(vHostName) == null) {
                logger.error("vHost: \"{}\" is not present", vHostName);
                return;
            }

            Channel channel = getChannel();

            // check channel
            if (!channel.isOpen()) {
                logger.warn("RabbitMQ connection is broken, trying to reconnect...");
                closeChannel();
                channel = getChannel();
                logger.info("RabbitMQ connection has been restored");
            }

            channel.exchangeDelete(exchangeName);

        } catch (Exception e) {
            logger.error("Exception deleting exchange: \"{}\" from vHost: \"{}\"", exchangeName, vHostName, e);
            throw e;
        }
    }

    public static Channel getChannel() {
        return getChannelContext().channel;
    }

    public static void closeChannel() {
        getChannelContext().close();
    }

    public static Map<String, Object> generateQueueArguments(PinSettings pinSettings) throws NumberFormatException {
        if (pinSettings == null) {
            return Collections.emptyMap();
        }
        if (pinSettings.getStorageOnDemand().equals("true")) {
            return Collections.emptyMap();
        } else {
            Map<String, Object> args = new HashMap<>();
            int queueLength = Integer.parseInt(pinSettings.getQueueLength());
            args.put("x-max-length", queueLength);
            args.put("x-overflow", pinSettings.getOverloadStrategy());
            return args;
        }
    }

    public static List<QueueInfo> getQueues() {

        String vHostName = getManagementConfig().getVHostName();

        try {
            Client rmqClient = getClient();
            return rmqClient.getQueues(vHostName);
        } catch (Exception e) {
            String message = "Exception while fetching queues";
            logger.error(message, e);
            throw new NonTerminalException(message, e);
        }
    }

    public static QueueInfo getQueue(String queueName) {

        String vHostName = getManagementConfig().getVHostName();
        try {
            Client rmqClient = getClient();
            return rmqClient.getQueue(vHostName, queueName);
        } catch (Exception e) {
            String message = "Exception while fetching queue";
            logger.error(message, e);
            throw new NonTerminalException(message, e);
        }
    }

    private static RabbitMQManagementConfig getManagementConfig() {
        // we do not need to synchronize as we are assigning immutable object from singleton
        if (managementConfig == null) {
            managementConfig = OperatorConfig.INSTANCE.getRabbitMQManagementConfig();
        }
        return managementConfig;
    }

    private static Client getClient() throws Exception {
        if (rmqClient == null) {
            RabbitMQManagementConfig rabbitMQMngConfig = getManagementConfig();
            String apiStr = "http://%s:%s/api";
            rmqClient = new Client(new ClientParameters()
                    .url(format(apiStr, rabbitMQMngConfig.getHost(), rabbitMQMngConfig.getManagementPort()))
                    .username(rabbitMQMngConfig.getUsername())
                    .password(rabbitMQMngConfig.getPassword())
            );
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
            RabbitMQManagementConfig rabbitMQManagementConfig = getManagementConfig();
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setHost(rabbitMQManagementConfig.getHost());
            connectionFactory.setPort(rabbitMQManagementConfig.getApplicationPort());
            connectionFactory.setVirtualHost(rabbitMQManagementConfig.getVHostName());
            connectionFactory.setUsername(rabbitMQManagementConfig.getUsername());
            connectionFactory.setPassword(rabbitMQManagementConfig.getPassword());
            try {
                this.connection = connectionFactory.newConnection();
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
        }
    }
}
