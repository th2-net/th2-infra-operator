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
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionSpec;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class CustomResourceUtils {

    private static final String DEFAULT_NAMESPACE = "default";
    private static final Logger logger = LoggerFactory.getLogger(CustomResourceUtils.class);

    private CustomResourceUtils() {
        throw new AssertionError();
    }


    public static CustomResourceDefinition getResourceCrd(KubernetesClient client, String crdName) {

        CustomResourceDefinition crd = client.customResourceDefinitions().withName(crdName).get();
        if (crd == null)
            throw new IllegalStateException(String.format("CRD with name '%s' not found", crdName));
        return crd;
    }


    public static void createResourceCrd(KubernetesClient kubClient, CustomResourceDefinition crd, String resourceType) {

        try {
            kubClient.customResourceDefinitions().createOrReplace(crd);
            logger.info("Created CRD for '{}'", resourceType);
        } catch (Exception e) {
            logger.error("Exception creating CRD for '{}'", resourceType, e);
        }
    }


    public static boolean isResourceCrdExist(KubernetesClient kubClient, CustomResourceDefinition crd) {

        final String crdName = ExtractUtils.extractName(crd);
        final String extractedNamespace = ExtractUtils.extractNamespace(crd);
        final String crdNamespace = (extractedNamespace == null) ? DEFAULT_NAMESPACE : extractedNamespace;

        return kubClient.customResourceDefinitions()
                .list()
                .getItems()
                .stream()
                .anyMatch(inCrd -> {

                    String inCrdName = ExtractUtils.extractName(inCrd);
                    String inCrdNamespace = ExtractUtils.extractNamespace(inCrd);
                    if (inCrdNamespace == null)
                        inCrdNamespace = DEFAULT_NAMESPACE;
                    return inCrdName.equals(crdName) && inCrdNamespace.equals(crdNamespace);

                });
    }


    public static <T extends CustomResource> Watch watchFor(ResourceClient<T> resourceClient, Watcher<T> watcher) {

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
        CustomResourceDefinitionSpec spec = crd.getSpec();

        String apiVersion = spec.getGroup() + "/" + spec.getVersion();
        String kind = spec.getNames().getKind();

        KubernetesDeserializer.registerCustomKind(apiVersion, kind, resourceType);

        Watch watch = crClient.inAnyNamespace().watch(watcher);
        logger.info("Started watching for \"{}\" resources", kind);
        return watch;
    }

}
