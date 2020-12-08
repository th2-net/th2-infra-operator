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
import com.exactpro.th2.infraoperator.fabric8.configuration.RabbitMQConfig;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.ConfigNotFoundException;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.VHostCreateException;
import com.rabbitmq.http.client.Client;
import com.rabbitmq.http.client.ClientParameters;
import com.rabbitmq.http.client.OkHttpRestTemplateConfigurator;
import com.rabbitmq.http.client.domain.UserPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class MqVHostUtils {

    private static final Logger logger = LoggerFactory.getLogger(MqVHostUtils.class);

    private static Client getClient(String apiUrl, String username, String password) throws Exception {
        return new Client(new ClientParameters()
                        .url(apiUrl)
                        .username(username)
                        .password(password)
                        .restTemplateConfigurator(new OkHttpRestTemplateConfigurator())
        );
    }

    public static void createVHostIfAbsent(String namespace, OperatorConfig.RabbitMQManagementConfig rabbitMQManagementConfig) throws VHostCreateException {

        RabbitMQConfig rabbitMQConfig = OperatorConfig.INSTANCE.getRabbitMQConfig4Namespace(namespace);

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
                OperatorConfig.MqSchemaUserPermissions schemaUserPermissions = rabbitMQManagementConfig.getSchemaUserPermissions();
                UserPermissions permissions = new UserPermissions();
                permissions.setConfigure(schemaUserPermissions.getConfigure());
                permissions.setRead(schemaUserPermissions.getRead());
                permissions.setWrite(schemaUserPermissions.getWrite());

                rmqClient.updatePermissions(vHostName, username, permissions);
                logger.info("User \"{}\" permissions set in RabbitMQ", username);
//            } else
//                logger.info("User \"{}\" was already present in RabbitMQ", username);
        } catch (Exception e) {
            logger.error("Exception setting up vHost & user for namespace \"{}\"", namespace, e);
            throw new VHostCreateException(e);
        }
    }


    public static void cleanupVHost(String namespace, OperatorConfig.RabbitMQManagementConfig rabbitMQManagementConfig) throws VHostCreateException {

        RabbitMQConfig rabbitMQConfig = OperatorConfig.INSTANCE.getRabbitMQConfig4Namespace(namespace);

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
