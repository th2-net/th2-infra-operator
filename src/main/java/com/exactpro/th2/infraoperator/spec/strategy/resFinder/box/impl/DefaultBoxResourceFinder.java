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

package com.exactpro.th2.infraoperator.spec.strategy.resFinder.box.impl;

import com.exactpro.th2.infraoperator.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.box.BoxResourceFinder;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;


public class DefaultBoxResourceFinder implements BoxResourceFinder {

    private List<ResourceClient<Th2CustomResource>> resourceClients;


    public DefaultBoxResourceFinder(List<ResourceClient<Th2CustomResource>> resourceClients) {
        this.resourceClients = resourceClients;
    }


    @Nullable
    @Override
    public Th2CustomResource getResource(String name, String namespace, Th2CustomResource... additionalSource) {
        for (var r : additionalSource) {
            if (Objects.nonNull(r) && ExtractUtils.extractName(r).equals(name) && ExtractUtils.extractNamespace(r).equals(namespace)) {
                return r;
            }
        }

        for (var resourceClient : resourceClients) {
            for (var cr : resourceClient.getInstance().inNamespace(namespace).list().getItems()) {
                if (ExtractUtils.extractName(cr).equals(name) && ExtractUtils.extractNamespace(cr).equals(namespace)) {
                    return cr;
                }
            }
        }

        return null;
    }

    @Override
    public List<Th2CustomResource> getResources() {
        return getResources(rc -> rc.getInstance().inAnyNamespace().list());
    }

    @Override
    public List<Th2CustomResource> getResources(String namespace) {
        return getResources(rc -> rc.getInstance().inNamespace(namespace).list());
    }


    private List<Th2CustomResource> getResources(
            Function<ResourceClient<Th2CustomResource>, KubernetesResourceList<Th2CustomResource>> listFunction
    ) {
        return resourceClients.stream()
                .flatMap(rc -> listFunction.apply(rc).getItems().stream())
                .collect(Collectors.toList());
    }

}
