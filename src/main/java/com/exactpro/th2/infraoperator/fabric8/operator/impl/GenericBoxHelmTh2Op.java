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
import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.ipml.GenericBoxClient;
import com.exactpro.th2.infraoperator.fabric8.operator.BoxHelmTh2Op;
import com.exactpro.th2.infraoperator.fabric8.operator.context.HelmOperatorContext;
import com.exactpro.th2.infraoperator.fabric8.spec.generic.Th2GenericBox;
import io.fabric8.kubernetes.client.KubernetesClient;

public class GenericBoxHelmTh2Op extends BoxHelmTh2Op<Th2GenericBox> {

    private final GenericBoxClient genericBoxClient;


    public GenericBoxHelmTh2Op(HelmOperatorContext.Builder<?, ?> builder) {
        super(builder);
        this.genericBoxClient = new GenericBoxClient(builder.getClient());
    }


    @Override
    public ResourceClient<Th2GenericBox> getResourceClient() {
        return genericBoxClient;
    }


    @Override
    protected String getKubObjDefPath(Th2GenericBox resource) {
        return "/cr/helm/th2-generic-box-helm-release-live.yml";
    }


    public static Builder builder(KubernetesClient client) {
        return new Builder(client);
    }

    public static class Builder extends HelmOperatorContext.Builder<GenericBoxHelmTh2Op, Builder> {

        public Builder(KubernetesClient client) {
            super(client);
        }

        @Override
        public GenericBoxHelmTh2Op build() {
            return new GenericBoxHelmTh2Op(this);
        }

        @Override
        protected Builder self() {
            return this;
        }

    }

}
