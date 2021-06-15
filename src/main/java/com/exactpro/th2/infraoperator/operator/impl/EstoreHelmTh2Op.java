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
import com.exactpro.th2.infraoperator.model.kubernetes.client.impl.EstoreClient;
import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op;
import com.exactpro.th2.infraoperator.operator.context.HelmOperatorContext;
import com.exactpro.th2.infraoperator.spec.estore.Th2Estore;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;

public class EstoreHelmTh2Op extends StoreHelmTh2Op<Th2Estore> {

    private final EstoreClient estoreClient;

    public EstoreHelmTh2Op(HelmOperatorContext.Builder<?, ?> builder) {
        super(builder);
        this.estoreClient = new EstoreClient(builder.getClient());
    }

    @Override
    public SharedIndexInformer<Th2Estore> generateInformerFromFactory(SharedInformerFactory factory) {
        return factory.sharedIndexInformerForCustomResource(
                Th2Estore.class,
                CustomResourceUtils.RESYNC_TIME);
    }

    @Override
    public ResourceClient<Th2Estore> getResourceClient() {
        return estoreClient;
    }

    @Override
    protected String getKubObjDefPath(Th2Estore resource) {
        return "/Th2Estore-HelmRelease.yml";
    }

    @Override
    protected String getStorageName() {
        return EVENT_STORAGE_BOX_ALIAS;
    }

    public static Builder builder(KubernetesClient client) {
        return new Builder(client);
    }

    public static class Builder extends HelmOperatorContext.Builder<EstoreHelmTh2Op, Builder> {

        public Builder(KubernetesClient client) {
            super(client);
        }

        @Override
        public EstoreHelmTh2Op build() {
            return new EstoreHelmTh2Op(this);
        }

    }
}
