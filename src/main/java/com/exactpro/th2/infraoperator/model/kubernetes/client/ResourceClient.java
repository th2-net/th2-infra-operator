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

package com.exactpro.th2.infraoperator.model.kubernetes.client;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;


/**
 * Provides information about the client of a custom resource.
 *
 * @param <CR> type of custom resource
 */
public interface ResourceClient<CR extends CustomResource> {

    /**
     * @return class of custom resource
     */
    Class<CR> getResourceType();

    /**
     * @return custom resource definition of custom resource
     */
    CustomResourceDefinition getCustomResourceDefinition();

    /**
     * @return kubernetes custom client of custom resource
     */
    MixedOperation<CR, ? extends KubernetesResourceList<CR>, ? extends Doneable<CR>, ? extends Resource<CR, ? extends Doneable<CR>>> getInstance();

}
