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

package com.exactpro.th2.infraoperator.operator.impl;

import com.exactpro.th2.infraoperator.model.box.mq.factory.MessageRouterConfigFactory;
import com.exactpro.th2.infraoperator.model.box.mq.factory.MessageRouterConfigFactoryBox;
import com.exactpro.th2.infraoperator.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.model.kubernetes.client.impl.JobClient;
import com.exactpro.th2.infraoperator.operator.GenericHelmTh2Op;
import com.exactpro.th2.infraoperator.spec.job.Th2Job;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;

public class JobHelmTh2Op extends GenericHelmTh2Op<Th2Job> {

    private final JobClient jobClient;

    public JobHelmTh2Op(KubernetesClient kubClient, RabbitMQContext rabbitMQContext) {
        super(kubClient, rabbitMQContext);
        this.jobClient = new JobClient(kubClient);
    }

    @Override
    public SharedIndexInformer<Th2Job> generateInformerFromFactory(SharedInformerFactory factory) {
        return factory.sharedIndexInformerFor(
                Th2Job.class,
                CustomResourceUtils.RESYNC_TIME);
    }

    @Override
    public ResourceClient<Th2Job> getResourceClient() {
        return jobClient;
    }

    @Override
    protected MessageRouterConfigFactory getMqConfigFactory() {
        return new MessageRouterConfigFactoryBox();
    }

}
