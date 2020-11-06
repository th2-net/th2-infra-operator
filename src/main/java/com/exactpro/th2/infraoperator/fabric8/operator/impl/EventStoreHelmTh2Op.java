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

package com.exactpro.th2.infraoperator.fabric8.operator.impl;

import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.ipml.EventStoreClient;
import com.exactpro.th2.infraoperator.fabric8.operator.StoreHelmTh2Op;
import com.exactpro.th2.infraoperator.fabric8.operator.context.HelmOperatorContext;
import com.exactpro.th2.infraoperator.fabric8.spec.estore.Th2EventStore;
import io.fabric8.kubernetes.client.KubernetesClient;

public class EventStoreHelmTh2Op extends StoreHelmTh2Op<Th2EventStore> {

    private final EventStoreClient eventStoreClient;


    public EventStoreHelmTh2Op(HelmOperatorContext.Builder<?, ?> builder) {
        super(builder);
        this.eventStoreClient = new EventStoreClient(builder.getClient());
    }


    @Override
    public ResourceClient<Th2EventStore> getResourceClient() {
        return eventStoreClient;
    }


    @Override
    protected String getKubObjDefPath(Th2EventStore resource) {
        return "/cr/helm/th2-event-store-helm-release-live.yml";
    }

    @Override
    protected String getStorageName() {
        return EVENT_STORAGE_BOX_ALIAS;
    }


    public static Builder builder(KubernetesClient client) {
        return new Builder(client);
    }

    public static class Builder extends HelmOperatorContext.Builder<EventStoreHelmTh2Op, Builder> {

        public Builder(KubernetesClient client) {
            super(client);
        }

        @Override
        public EventStoreHelmTh2Op build() {
            return new EventStoreHelmTh2Op(this);
        }

        @Override
        protected Builder self() {
            return this;
        }

    }
}
