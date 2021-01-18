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

package com.exactpro.th2.infraoperator.spec.strategy.linkResolver.mq.impl;

import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.configuration.RabbitMQManagementConfig;
import com.exactpro.th2.infraoperator.configuration.RabbitMQNamespacePermissions;
import com.exactpro.th2.infraoperator.configuration.RabbitMQConfig;
import com.exactpro.th2.infraoperator.model.kubernetes.configmaps.ConfigMaps;
import com.exactpro.th2.infraoperator.spec.shared.PinSettings;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.ConfigNotFoundException;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.VHostCreateException;
import com.exactpro.th2.infraoperator.util.Strings;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.http.client.Client;
import com.rabbitmq.http.client.ClientParameters;
import com.rabbitmq.http.client.OkHttpRestTemplateConfigurator;
import com.rabbitmq.http.client.domain.QueueInfo;
import com.rabbitmq.http.client.domain.UserPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RabbitMQContext {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQContext.class);

    private static final Map<String, ChannelContext> channelContexts = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> exchangeResets = new ConcurrentHashMap<>();

    private RabbitMQContext() {
    }


    private static volatile RabbitMQManagementConfig managementConfig;
    private static RabbitMQManagementConfig getManagementConfig() {
        // we do not need to synchronize as we are assigning immutable object from singleton
        if (managementConfig == null)
            managementConfig = OperatorConfig.INSTANCE.getRabbitMQManagementConfig();
        return managementConfig;
    }

    // call to this method should be synchronized externally per namespace
    public static Channel getChannel(String namespace) {

        var rabbitMQManagementConfig = getManagementConfig();
        var rabbitMQConfig = getRabbitMQConfig(namespace);

        String signature = ChannelContext.signatureFor(rabbitMQManagementConfig, rabbitMQConfig);

        var context = channelContexts.computeIfAbsent(namespace, k -> ChannelContext.contextFor(rabbitMQConfig));

        // check if we need to recreate channel for this namespace
        // due to configuration change
        if (!context.signature.equals(signature)) {
            context = ChannelContext.contextFor(rabbitMQConfig);
            channelContexts.put(namespace, context);
        }

        return context.channel;
    }


    public static RabbitMQConfig getRabbitMQConfig(String namespace) throws ConfigNotFoundException {

        RabbitMQConfig rabbitMQConfig = ConfigMaps.INSTANCE.getRabbitMQConfig4Namespace(namespace);
        if (rabbitMQConfig == null)
            throw new ConfigNotFoundException(String.format("RabbitMQ configuration for namespace \"%s\" is not available", namespace));
        return rabbitMQConfig;
    }


    public static String getExchangeName(String namespace) {

        return getRabbitMQConfig(namespace).getExchangeName();
    }


    // call to this method should be synchronized externally per namespace
    public static void closeChannel(String namespace) {

        var context = channelContexts.get(namespace);
        if (context != null) {
            context.close();
            channelContexts.remove(namespace);
        }
    }


    public static boolean isExchangeReset(String namespace) {
        Boolean reset = exchangeResets.get(namespace);
        return (reset != null) && reset;
    }


    public static void markExchangeReset(String namespace) {
        exchangeResets.put(namespace, Boolean.TRUE);
    }


    public static Map<String, Object> generateQueueArguments(PinSettings pinSettings) throws NumberFormatException {

        if (pinSettings.getStorageOnDemand().equals("true")) {
            return Collections.emptyMap();
        } else {
            Map<String, Object> args = new HashMap<>();
            int queueLength = Integer.parseInt(pinSettings.getQueueLength());
            args.put("x-max-length", queueLength);
            return args;
        }
    }


    private static Client getClient(String apiUrl, String username, String password) throws Exception {
        return new Client(new ClientParameters()
            .url(apiUrl)
            .username(username)
            .password(password)
            .restTemplateConfigurator(new OkHttpRestTemplateConfigurator())
        );
    }


    public static void createVHostIfAbsent(String namespace)
        throws VHostCreateException {

        RabbitMQManagementConfig rabbitMQManagementConfig = getManagementConfig();
        RabbitMQConfig rabbitMQConfig = getRabbitMQConfig(namespace);

        String vHostName = rabbitMQConfig.getVHost();
        String username = rabbitMQConfig.getUsername();

        if (Strings.isNullOrEmpty(username))
            return;

        try {
            Client rmqClient = getClient(
                String.format("http://%s:%s/api", rabbitMQConfig.getHost(), rabbitMQManagementConfig.getPort())
                , rabbitMQManagementConfig.getUsername()
                , rabbitMQManagementConfig.getPassword()
            );

            // check vhost
            if (rmqClient.getVhost(vHostName) == null) {
                rmqClient.createVhost(vHostName);
                logger.info("Created vHost in RabbitMQ for namespace \"{}\"", namespace);
            } else
                logger.info("vHost \"{}\" was already present in RabbitMQ", vHostName);

            // check user
//            if (rmqClient.getUser(username) == null) {
            rmqClient.createUser(username, rabbitMQConfig.getPassword().toCharArray(), new ArrayList<>());
            logger.info("Created user \"{}\" in RabbitMQ for namespace \"{}\"", username, namespace);

            // set permissions
            RabbitMQNamespacePermissions rabbitMQNamespacePermissions =
                rabbitMQManagementConfig.getRabbitMQNamespacePermissions();
            UserPermissions permissions = new UserPermissions();
            permissions.setConfigure(rabbitMQNamespacePermissions.getConfigure());
            permissions.setRead(rabbitMQNamespacePermissions.getRead());
            permissions.setWrite(rabbitMQNamespacePermissions.getWrite());

            rmqClient.updatePermissions(vHostName, username, permissions);
            logger.info("User \"{}\" permissions set in RabbitMQ", username);
//            } else
//                logger.info("User \"{}\" was already present in RabbitMQ", username);
        } catch (Exception e) {
            logger.error("Exception setting up vHost & user for namespace \"{}\"", namespace, e);
            throw new VHostCreateException(e);
        }
    }

    public static void cleanupVHost(String namespace)
        throws VHostCreateException {

        RabbitMQManagementConfig rabbitMQManagementConfig = getManagementConfig();
        RabbitMQConfig rabbitMQConfig = getRabbitMQConfig(namespace);

        String vHostName = rabbitMQConfig.getVHost();
        String username = rabbitMQConfig.getUsername();

        try {
            Client rmqClient = getClient(
                String.format("http://%s:%s/api", rabbitMQConfig.getHost(), rabbitMQManagementConfig.getPort())
                , rabbitMQManagementConfig.getUsername()
                , rabbitMQManagementConfig.getPassword()
            );

            // delete user
            if (rmqClient.getUser(username) != null) {
                rmqClient.deleteUser(username);
                logger.info("Deleted user \"{}\" in RabbitMQ", username);
            }

            // delete vhost
            if (rmqClient.getVhost(vHostName) != null) {
                rmqClient.deleteVhost(vHostName);
                logger.info("Deleted vHost \"{}\" in RabbitMQ", username);
            }
        } catch (Exception e) {
            logger.error("Exception cleaning up vHost  \"{}\"", vHostName, e);
            throw new VHostCreateException(e);
        }
    }


    public static List<QueueInfo> getQueues(String vhost) throws Exception {

        RabbitMQManagementConfig rabbitMQManagementConfig = getManagementConfig();
        try {
            Client rmqClient = getClient(
                    String.format("http://%s:%s/api", rabbitMQManagementConfig.getHost(), rabbitMQManagementConfig.getPort())
                    , rabbitMQManagementConfig.getUsername()
                    , rabbitMQManagementConfig.getPassword()
            );
            return rmqClient.getQueues(vhost);
        } catch (Exception e) {
            logger.error("Exception while fetching queues for vHost: '{}'", vhost, e);
            throw e;
        }
    }


    static class ChannelContext {

        private static final Map<String, ConnectionFactory> connectionFactories = new ConcurrentHashMap<>();

        private final String signature;
        private Connection connection;
        private Channel channel;


        ChannelContext(ConnectionFactory factory, String signature) {
            this.signature = signature;
            try {
                this.connection = factory.newConnection();
                this.channel = connection.createChannel();
            } catch (Exception e) {
                close();
                throw new RuntimeException(e);
            }
        }


        static ChannelContext contextFor(RabbitMQConfig namespaceConfig) {

            RabbitMQManagementConfig rabbitMQManagementConfig = getManagementConfig();
            String signature = signatureFor(rabbitMQManagementConfig, namespaceConfig);
            var connectionFactory = getConnectionFactory(namespaceConfig);
            return new ChannelContext(connectionFactory, signature);
        }


        static ConnectionFactory getConnectionFactory(RabbitMQConfig rabbitMQConfig) {

            RabbitMQManagementConfig rabbitMQManagementConfig = getManagementConfig();
            String signature = ChannelContext.signatureFor(rabbitMQManagementConfig, rabbitMQConfig);
            return connectionFactories.computeIfAbsent(signature, k -> {

                var connectionFactory = new ConnectionFactory();
                connectionFactory.setHost(rabbitMQConfig.getHost());
                connectionFactory.setPort(rabbitMQConfig.getPort());
                connectionFactory.setVirtualHost(rabbitMQConfig.getVHost());
                connectionFactory.setUsername(rabbitMQManagementConfig.getUsername());
                connectionFactory.setPassword(rabbitMQManagementConfig.getPassword());
                return connectionFactory;
            });
        }


        synchronized void close() {
            try {
                if (channel != null && channel.isOpen())
                    channel.close();
            } catch (Exception e) {
                logger.error("Exception closing RabbitMQ channel for \"{}\"", signature, e);
            }
            try {
                if (connection != null && connection.isOpen())
                    connection.close();
            } catch (Exception e) {
                logger.error("Exception closing RabbitMQ connection for \"{}\"", signature, e);
            }
            channel = null;
            connection = null;
        }


        static String signatureFor(RabbitMQManagementConfig managementConfig, RabbitMQConfig rabbitMQConfig) {
            return
                    rabbitMQConfig.getHost()
                            + ":" +rabbitMQConfig.getPort()
                            + ":" + rabbitMQConfig.getVHost()
                            + ":" + managementConfig.getUsername();
        }

    }
}
