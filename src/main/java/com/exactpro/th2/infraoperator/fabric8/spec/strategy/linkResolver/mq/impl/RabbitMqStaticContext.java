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
import com.exactpro.th2.infraoperator.fabric8.spec.shared.PinSettings;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.ConfigNotFoundException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RabbitMqStaticContext {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMqStaticContext.class);

    private static final Map<String, ChannelBunch> mqChannels = new ConcurrentHashMap<>();

    private static final Map<String, Boolean> mqExchangeResets = new ConcurrentHashMap<>();

    @SneakyThrows
    public static void createChannelIfAbsent(
            String namespace,
            RabbitMQManagementConfig rabbitMQManagementConfig,
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

        RabbitMQConfig rabbitMQConfig = OperatorConfig.INSTANCE.getRabbitMQConfig4Namespace(namespace);

        if (rabbitMQConfig == null) {

            String message = String.format(
                    "Cannot find vHost and exchange in namespace '%s'. " +
                            "Perhaps config map '%s.%s' does not exist or " +
                            "is not watching yet, or property '%s' is not set",
                    namespace, namespace, OperatorConfig.INSTANCE.getRabbitMQConfigMapName(), RabbitMQConfig.CONFIG_MAP_RABBITMQ_PROP_NAME);
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
}
