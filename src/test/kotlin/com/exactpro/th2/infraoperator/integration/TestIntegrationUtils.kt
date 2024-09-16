/*
 * Copyright 2024-2024 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.infraoperator.integration

import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext
import com.rabbitmq.http.client.Client
import org.slf4j.LoggerFactory
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.k3s.K3sContainer
import org.testcontainers.lifecycle.Startable
import org.testcontainers.utility.DockerImageName

private val K3S_DOCKER_IMAGE = DockerImageName.parse("rancher/k3s:v1.21.3-k3s1")
private val RABBITMQ_DOCKER_IMAGE = DockerImageName.parse("rabbitmq:3.12.6-management")

fun createK3sContainer(): K3sContainer = K3sContainer(K3S_DOCKER_IMAGE)
    .withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("K3S")).withSeparateOutputStreams())
    .also(Startable::start)

fun createRabbitMQContainer(): RabbitMQContainer = RabbitMQContainer(RABBITMQ_DOCKER_IMAGE)
    .withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("RABBIT_MQ")).withSeparateOutputStreams())
    .also(Startable::start)

fun createRabbitMQClient(rabbitMQ: RabbitMQContainer): Client =
    RabbitMQContext.createClient(
        rabbitMQ.host,
        rabbitMQ.httpPort,
        rabbitMQ.adminUsername,
        rabbitMQ.adminPassword,
    )