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
import com.exactpro.th2.infraoperator.fabric8.configuration.RabbitMQConfig;
import com.exactpro.th2.infraoperator.fabric8.configuration.RabbitMQManagementConfig;
import com.exactpro.th2.infraoperator.fabric8.configuration.RabbitMQNamespacePermissions;
import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.configmaps.ConfigMaps;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.PinSettings;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.ConfigNotFoundException;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.VHostCreateException;
import com.exactpro.th2.infraoperator.fabric8.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.fabric8.util.Strings;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.http.client.Client;
import com.rabbitmq.http.client.ClientParameters;
import com.rabbitmq.http.client.OkHttpRestTemplateConfigurator;
import com.rabbitmq.http.client.domain.UserPermissions;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RabbitMqStaticContext {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMqStaticContext.class);

    private static final Map<String, ChannelBunch> mqChannels = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> mqExchangeResets = new ConcurrentHashMap<>();

    @SneakyThrows
    public static void createChannelIfAbsent(String namespace, RabbitMQManagementConfig rabbitMQManagementConfig,
                                             ConnectionFactory connectionFactory) {

        RabbitMQConfig rabbitMQConfig = getRabbitMQConfig(namespace);
        ChannelBunch channelBunch = mqChannels.get(namespace);

        if (channelBunch == null || !channelBunch.getConfig().equals(rabbitMQConfig)) {

            connectionFactory.setHost(rabbitMQConfig.getHost());
            connectionFactory.setPort(rabbitMQConfig.getPort());
            connectionFactory.setVirtualHost(rabbitMQConfig.getVHost());
            connectionFactory.setUsername(rabbitMQManagementConfig.getUsername());
            connectionFactory.setPassword(rabbitMQManagementConfig.getPassword());

            var channel = connectionFactory.newConnection().createChannel();

            mqChannels.put(namespace, new ChannelBunch(channel, rabbitMQConfig));
        }
    }

    public static RabbitMQConfig getRabbitMQConfig(String namespace) throws ConfigNotFoundException {

        RabbitMQConfig rabbitMQConfig = ConfigMaps.INSTANCE.getRabbitMQConfig4Namespace(namespace);

        if (rabbitMQConfig == null) {

            String message = String.format(
                "Cannot find vHost and exchange in namespace '%s'. " +
                    "Perhaps config map '%s.%s' does not exist or " +
                    "is not watching yet, or property '%s' is not set",
                namespace, namespace, OperatorConfig.INSTANCE.getRabbitMQConfigMapName(),
                RabbitMQConfig.CONFIG_MAP_RABBITMQ_PROP_NAME);
            logger.warn(message);

            throw new ConfigNotFoundException(message);
        }
        return rabbitMQConfig;
    }

    @Getter
    @AllArgsConstructor
    public static class ChannelBunch {

        private final Channel channel;
        private final RabbitMQConfig config;
    }

    public static Map<String, ChannelBunch> getMqChannels() {
        return mqChannels;
    }

    public static Map<String, Boolean> getMqExchangeResets() {
        return mqExchangeResets;
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

    // MqVHostUtils
    private static Client getClient(String apiUrl, String username, String password) throws Exception {
        return new Client(new ClientParameters()
            .url(apiUrl)
            .username(username)
            .password(password)
            .restTemplateConfigurator(new OkHttpRestTemplateConfigurator())
        );
    }

    public static void createVHostIfAbsent(String namespace, RabbitMQManagementConfig rabbitMQManagementConfig)
        throws VHostCreateException {

        RabbitMQConfig rabbitMQConfig = ConfigMaps.INSTANCE.getRabbitMQConfig4Namespace(namespace);

        if (rabbitMQConfig == null)
            throw new ConfigNotFoundException(String.format(
                "Exception setting up RabbitMQ for namespace \"%s\". Check if \"%s\" is configured properly"
                , namespace
                , CustomResourceUtils.annotationFor(
                    namespace, "ConfigMap", OperatorConfig.INSTANCE.getRabbitMQConfigMapName())));

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

    public static void cleanupVHost(String namespace, RabbitMQManagementConfig rabbitMQManagementConfig)
        throws VHostCreateException {

        RabbitMQConfig rabbitMQConfig = ConfigMaps.INSTANCE.getRabbitMQConfig4Namespace(namespace);

        if (rabbitMQConfig == null)
            throw new ConfigNotFoundException(String.format(
                "Exception cleaning up RabbitMQ for namespace \"%s\". Check if \"%s\" is configured properly"
                , namespace
                , CustomResourceUtils.annotationFor(
                    namespace, "ConfigMap", OperatorConfig.INSTANCE.getRabbitMQConfigMapName())));

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
}
