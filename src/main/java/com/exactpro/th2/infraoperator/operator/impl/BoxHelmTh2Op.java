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
import com.exactpro.th2.infraoperator.model.kubernetes.client.impl.BoxClient;
import com.exactpro.th2.infraoperator.operator.GenericHelmTh2Op;
import com.exactpro.th2.infraoperator.operator.context.HelmOperatorContext;
import com.exactpro.th2.infraoperator.spec.box.Th2Box;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;

public class BoxHelmTh2Op extends GenericHelmTh2Op<Th2Box> {

    private final BoxClient boxClient;

    public BoxHelmTh2Op(HelmOperatorContext.Builder<?, ?> builder) {
        super(builder);
        this.boxClient = new BoxClient(builder.getClient());
    }

    @Override
    public SharedIndexInformer<Th2Box> generateInformerFromFactory(SharedInformerFactory factory) {
        return factory.sharedIndexInformerFor(
                Th2Box.class,
                CustomResourceUtils.RESYNC_TIME);
    }

    @Override
    public ResourceClient<Th2Box> getResourceClient() {
        return boxClient;
    }

    @Override
    protected String getKubObjDefPath(Th2Box resource) {
        return "/Th2Box-HelmRelease.yml";
    }

    public static Builder builder(KubernetesClient client) {
        return new Builder(client);
    }

    public static class Builder extends HelmOperatorContext.Builder<BoxHelmTh2Op, Builder> {

        public Builder(KubernetesClient client) {
            super(client);
        }

        @Override
        public BoxHelmTh2Op build() {
            return new BoxHelmTh2Op(this);
        }

    }

}
