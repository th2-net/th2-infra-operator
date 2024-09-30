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

import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.BindQueueLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.DeclareQueueResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext;

import java.util.Collection;

public class RecreateQueuesAndBindings implements Task {
    private final RabbitMQContext rabbitMQContext;

    private final DeclareQueueResolver declareQueueResolver;

    private final BindQueueLinkResolver bindQueueLinkResolver;

    private final long retryDelay;

    private final Collection<Th2CustomResource> resources;

    public RecreateQueuesAndBindings(RabbitMQContext rabbitMQContext,
                                     Collection<Th2CustomResource> resources,
                                     long retryDelay) {
        this.rabbitMQContext = rabbitMQContext;
        this.declareQueueResolver = new DeclareQueueResolver(rabbitMQContext);
        this.bindQueueLinkResolver = new BindQueueLinkResolver(rabbitMQContext);
        this.resources = resources;
        this.retryDelay = retryDelay;
    }

    @Override
    public String getName() {
        return RecreateQueuesAndBindings.class.getName();
    }

    @Override
    public long getRetryDelay() {
        return retryDelay;
    }

    @Override
    public void run() {
        rabbitMQContext.getChannel();
        resources.forEach(resource -> {
            declareQueueResolver.resolveAdd(resource);
            bindQueueLinkResolver.resolveDeclaredLinks(resource);
            bindQueueLinkResolver.resolveHiddenLinks(resource);
        });
    }
}
