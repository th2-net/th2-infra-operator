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

package com.exactpro.th2.infraoperator.operator.impl;

import com.exactpro.th2.infraoperator.model.box.mq.factory.MessageRouterConfigFactory;
import com.exactpro.th2.infraoperator.model.box.mq.factory.MessageRouterConfigFactoryBox;
import com.exactpro.th2.infraoperator.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.model.kubernetes.client.impl.CoreBoxClient;
import com.exactpro.th2.infraoperator.operator.GenericHelmTh2Op;
import com.exactpro.th2.infraoperator.spec.corebox.Th2CoreBox;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;

public class CoreBoxHelmTh2Op extends GenericHelmTh2Op<Th2CoreBox> {

    private final CoreBoxClient coreBoxClient;

    public CoreBoxHelmTh2Op(KubernetesClient client) {
        super(client);
        this.coreBoxClient = new CoreBoxClient(client);
    }

    @Override
    public ResourceClient<Th2CoreBox> getResourceClient() {
        return coreBoxClient;
    }

    @Override
    public SharedIndexInformer<Th2CoreBox> generateInformerFromFactory(SharedInformerFactory factory) {
        return factory.sharedIndexInformerFor(
                Th2CoreBox.class,
                CustomResourceUtils.RESYNC_TIME);
    }

    @Override
    protected MessageRouterConfigFactory getMqConfigFactory() {
        return new MessageRouterConfigFactoryBox();
    }
}
