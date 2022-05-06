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

package com.exactpro.th2.infraoperator.model.kubernetes.client;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DefaultResourceClient<CR extends CustomResource> implements ResourceClient<CR> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultResourceClient.class);

    private final KubernetesClient client;

    private final Class<CR> resourceType;

    private final MixedOperation<CR, ? extends KubernetesResourceList<CR>, ? extends Resource<CR>> instance;

    private final String crdName;

    public DefaultResourceClient(
            KubernetesClient client,
            Class<CR> resourceType,
            String crdName
    ) {
        this.client = client;
        this.resourceType = resourceType;
        this.crdName = crdName;

        instance = client.resources(resourceType);
    }

    @Override
    public Class<CR> getResourceType() {
        return resourceType;
    }

    public KubernetesClient getClient() {
        return this.client;
    }

    public MixedOperation<CR, ? extends KubernetesResourceList<CR>, ? extends Resource<CR>> getInstance() {
        return this.instance;
    }

    public String getCrdName() {
        return this.crdName;
    }

    @Override
    public boolean equals(Object o) {
        throw new AssertionError("method not defined");
    }

    @Override
    public int hashCode() {
        throw new AssertionError("method not defined");
    }

    public String toString() {
        return "DefaultResourceClient(client=" +
                this.getClient() + ", resourceType=" + this.getResourceType() +
                ", instance=" + this.getInstance() + ", crdName=" + this.getCrdName() + ")";
    }
}

