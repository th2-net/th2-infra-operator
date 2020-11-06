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

package com.exactpro.th2.infraoperator.fabric8.util;

import com.exactpro.th2.infraoperator.fabric8.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.ConfigNotFoundException;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.VHostCreateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;

public class MqVHostUtils {

    private static final Logger logger = LoggerFactory.getLogger(MqVHostUtils.class);

    public static void createVHostIfAbsent(String namespace, OperatorConfig.MqGlobalConfig mqGlobalConfig) {

        OperatorConfig.MqWorkSpaceConfig mqWsConfig = OperatorConfig.INSTANCE.getMqWorkSpaceConfig(namespace);

        if (mqWsConfig == null) {

            String message = String.format(
                    "Cannot find vHost and exchange in namespace '%s'. " +
                            "Perhaps config map '%s.%s' does not exist or " +
                            "is not watching yet, or property '%s' is not set",
                    namespace, namespace, OperatorConfig.MQ_CONFIG_MAP_NAME, OperatorConfig.MqWorkSpaceConfig.CONFIG_MAP_RABBITMQ_PROP_NAME);
            logger.warn(message);

            throw new ConfigNotFoundException(message);
        }

        String vhost = mqWsConfig.getVHost();

        try {
            int getVHostResponseCode = HttpConnectionUtils.getRabbitMqVHost(mqGlobalConfig, mqWsConfig).getResponseCode();
            if (getVHostResponseCode >= HttpURLConnection.HTTP_OK && getVHostResponseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                logger.info("vHost \"{}\" already exists on the server, skipping", vhost);
            } else {
                int putVHostResponseCode = HttpConnectionUtils.putRabbitMqVHost(mqGlobalConfig, mqWsConfig).getResponseCode();
                if (putVHostResponseCode >= HttpURLConnection.HTTP_OK && putVHostResponseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                    logger.info("Created vHost for \"{}\" namespace", vhost);
                } else {
                    logger.error("couldn't create host for \"{}\" namespace. Server response code: \"{}\" ", vhost, putVHostResponseCode);
                    throw new VHostCreateException(String.format("couldn't create host for \"%s\" namespace. Server response code: %d ", vhost, putVHostResponseCode));
                }
            }
        } catch (Exception e) {
            logger.error("connection to rabbit failed with exception: {}", e.getMessage());
            throw new VHostCreateException(e.getMessage());
        }
    }
}
