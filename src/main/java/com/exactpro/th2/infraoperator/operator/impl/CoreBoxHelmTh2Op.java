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

import com.exactpro.th2.infraoperator.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.model.kubernetes.client.ipml.CoreBoxClient;
import com.exactpro.th2.infraoperator.operator.GenericHelmTh2Op;
import com.exactpro.th2.infraoperator.operator.context.HelmOperatorContext;
import com.exactpro.th2.infraoperator.spec.corebox.Th2CoreBox;
import com.exactpro.th2.infraoperator.spec.corebox.Th2CoreBoxList;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;

public class CoreBoxHelmTh2Op extends GenericHelmTh2Op<Th2CoreBox> {

    private final CoreBoxClient coreBoxClient;


    public CoreBoxHelmTh2Op(HelmOperatorContext.Builder<?, ?> builder) {
        super(builder);
        this.coreBoxClient = new CoreBoxClient(builder.getClient());
    }


    @Override
    public ResourceClient<Th2CoreBox> getResourceClient() {
        return coreBoxClient;
    }

    @Override
    public SharedIndexInformer<Th2CoreBox> generateInformerFromFactory (SharedInformerFactory factory) {
        return factory.sharedIndexInformerForCustomResource(
                CustomResourceDefinitionContext.fromCrd(coreBoxClient.getCustomResourceDefinition()),
                Th2CoreBox.class,
                Th2CoreBoxList.class,
                CustomResourceUtils.RESYNC_TIME);
    }
    @Override
    protected String getKubObjDefPath(Th2CoreBox resource) {
        return "/Th2CoreBox-HelmRelease.yml";
    }


    public static CoreBoxHelmTh2Op.Builder builder(KubernetesClient client) {
        return new CoreBoxHelmTh2Op.Builder(client);
    }

    public static class Builder extends HelmOperatorContext.Builder<CoreBoxHelmTh2Op, CoreBoxHelmTh2Op.Builder> {

        public Builder(KubernetesClient client) {
            super(client);
        }

        @Override
        public CoreBoxHelmTh2Op build() {
            return new CoreBoxHelmTh2Op(this);
        }

        @Override
        protected CoreBoxHelmTh2Op.Builder self() {
            return this;
        }

    }

}
