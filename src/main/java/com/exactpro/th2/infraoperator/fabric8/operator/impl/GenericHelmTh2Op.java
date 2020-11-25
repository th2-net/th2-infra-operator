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
import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.ipml.GenericClient;
import com.exactpro.th2.infraoperator.fabric8.operator.BoxHelmTh2Op;
import com.exactpro.th2.infraoperator.fabric8.operator.context.HelmOperatorContext;
import com.exactpro.th2.infraoperator.fabric8.spec.generic.Th2Generic;
import io.fabric8.kubernetes.client.KubernetesClient;

public class GenericHelmTh2Op extends BoxHelmTh2Op<Th2Generic> {

    private final GenericClient genericClient;


    public GenericHelmTh2Op(HelmOperatorContext.Builder<?, ?> builder) {
        super(builder);
        this.genericClient = new GenericClient(builder.getClient());
    }


    @Override
    public ResourceClient<Th2Generic> getResourceClient() {
        return genericClient;
    }


    @Override
    protected String getKubObjDefPath(Th2Generic resource) {
        return "/cr/helm/th2-generic-helm-release-live.yml";
    }


    public static Builder builder(KubernetesClient client) {
        return new Builder(client);
    }

    public static class Builder extends HelmOperatorContext.Builder<GenericHelmTh2Op, Builder> {

        public Builder(KubernetesClient client) {
            super(client);
        }

        @Override
        public GenericHelmTh2Op build() {
            return new GenericHelmTh2Op(this);
        }

        @Override
        protected Builder self() {
            return this;
        }

    }

}
