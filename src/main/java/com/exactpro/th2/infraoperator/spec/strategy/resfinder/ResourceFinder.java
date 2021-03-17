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

package com.exactpro.th2.infraoperator.spec.strategy.resfinder;

import io.fabric8.kubernetes.client.CustomResource;

import java.util.List;

/**
 * A factory that provides a th2 resource based on
 * the resource name and an additional list of resources
 * for filtering purpose if needed.
 */
public interface ResourceFinder<T extends CustomResource> {

    /**
     * Provides a th2 resource based on the resource name, namespace
     * and an additional list of resources for filtering purpose if needed.
     *
     * @param name             name of target resource
     * @param namespace        namespace of target resource
     * @param additionalSource additional resources for filtering purpose if needed
     * @return founded th2 resource with provided {@code name}
     */
    @SuppressWarnings("unchecked")
    T getResource(String name, String namespace, T... additionalSource);

    List<T> getResources(String namespace);

}
