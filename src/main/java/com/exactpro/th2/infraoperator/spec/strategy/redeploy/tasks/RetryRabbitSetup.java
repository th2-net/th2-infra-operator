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

package com.exactpro.th2.infraoperator.spec.strategy.redeploy.tasks;

import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext;

public class RetryRabbitSetup implements Task {

    private final RabbitMQContext rabbitMQContext;

    private final long retryDelay;

    private final String namespace;

    public RetryRabbitSetup(RabbitMQContext rabbitMQContext, String namespace, long retryDelay) {
        this.rabbitMQContext = rabbitMQContext;
        this.retryDelay = retryDelay;
        this.namespace = namespace;
    }

    @Override
    public String getName() {
        return String.format("%s:%s", RetryRabbitSetup.class.getName(), namespace);
    }

    @Override
    public long getRetryDelay() {
        return retryDelay;
    }

    @Override
    public void run() {
        rabbitMQContext.setUpRabbitMqForNamespace(namespace);
    }
}
