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

package com.exactpro.th2.infraoperator.fabric8.util;

import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.ResourceClient;
import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;


public final class CustomResourceUtils {

    private static final Logger logger = LoggerFactory.getLogger(CustomResourceUtils.class);


    public static final String DEFAULT_NAMESPACE = "default";


    private CustomResourceUtils() {
        throw new AssertionError();
    }


    public static CustomResourceDefinition getResourceCrd(KubernetesClient client, String crdName) {

        var crd = client.customResourceDefinitions().withName(crdName).get();

        if (Objects.isNull(crd)) {
            throw new IllegalStateException(String.format("Required CRD with name '%s' not found in kubernetes", crdName));
        }

        return crd;
    }

    public static void createResourceCrd(KubernetesClient kubClient, CustomResourceDefinition crd, String resourceType) {
        try {
            kubClient.customResourceDefinitions().createOrReplace(crd);

            logger.info("Created CRD of '{}'", resourceType);

        } catch (Exception e) {
            logger.error("Something went wrong while creating CRD of '{}'", resourceType, e);
        }
    }

    public static boolean isResourceCrdExist(KubernetesClient kubClient, CustomResourceDefinition crd) {
        return kubClient.customResourceDefinitions().list().getItems().stream()
                .anyMatch(inCrd -> {

                    var inCrdName = ExtractUtils.extractName(inCrd);
                    var inCrdNamespace = Objects.isNull(ExtractUtils.extractNamespace(inCrd))
                            ? DEFAULT_NAMESPACE
                            : ExtractUtils.extractNamespace(inCrd);

                    var crdName = ExtractUtils.extractName(crd);
                    var crdNamespace = Objects.isNull(ExtractUtils.extractNamespace(crd))
                            ? DEFAULT_NAMESPACE
                            : ExtractUtils.extractNamespace(crd);

                    return inCrdName.equals(crdName) && inCrdNamespace.equals(crdNamespace);

                });
    }

    public static <T extends CustomResource> Watch watchFor(
            ResourceClient<T> resourceClient,
            Watcher<T> watcher

    ) {
        return watchFor(
                watcher,
                resourceClient.getResourceType(),
                resourceClient.getCustomResourceDefinition(),
                resourceClient.getInstance()
        );
    }

    public static <T extends CustomResource, L extends KubernetesResourceList<T>, D extends Doneable<T>> Watch watchFor(
            Watcher<T> watcher,
            Class<T> resourceType,
            CustomResourceDefinition crd,
            MixedOperation<T, L, D, ? extends Resource<T, ? extends Doneable<T>>> crClient
    ) {
        var rMeta = getRegisterMeta(crd);

        KubernetesDeserializer.registerCustomKind(rMeta.getApiVersion(), rMeta.getKind(), resourceType);

        var connWatcher = crClient.inAnyNamespace().watch(watcher);

        logger.info("Started watching for [{}] resources", rMeta.getKind());

        return connWatcher;
    }


    private static RegisterMeta getRegisterMeta(CustomResourceDefinition resource) {
        var rawNamespace = resource.getMetadata().getNamespace();
        var namespace = Objects.isNull(rawNamespace) ? DEFAULT_NAMESPACE : rawNamespace;
        var spec = resource.getSpec();
        var apiVersion = spec.getGroup() + "/" + resource.getSpec().getVersion();
        var kind = spec.getNames().getKind();

        return new RegisterMeta(namespace, apiVersion, kind);
    }

    @Data
    @AllArgsConstructor
    private static class RegisterMeta {
        private String namespace;
        private String apiVersion;
        private String kind;
    }

}
