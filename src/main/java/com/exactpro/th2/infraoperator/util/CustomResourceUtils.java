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

package com.exactpro.th2.infraoperator.util;

import com.exactpro.th2.infraoperator.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.operator.manager.impl.DefaultWatchManager;
import com.exactpro.th2.infraoperator.operator.manager.impl.GenericResourceEventHandler;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionSpec;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class CustomResourceUtils {

    private static final String DEFAULT_NAMESPACE = "default";
    private static final Logger logger = LoggerFactory.getLogger(CustomResourceUtils.class);
    public static long RESYNC_TIME = 180000;

    private CustomResourceUtils() {
        throw new AssertionError();
    }


    public static String annotationFor(String namespace, String kind, String resourceName) {
        return String.format("%s:%s/%s", namespace, kind, resourceName);
    }

    public static String annotationFor(HasMetadata resource) {
        return annotationFor(
                  resource.getMetadata().getNamespace()
                , resource.getKind()
                , resource.getMetadata().getName()
        );
    }


    public static CustomResourceDefinition getResourceCrd(KubernetesClient client, String crdName) {

        CustomResourceDefinition crd = client.apiextensions().v1().customResourceDefinitions().withName(crdName).get();
        if (crd == null)
            throw new IllegalStateException(String.format("CRD with name '%s' not found", crdName));
        return crd;
    }


    public static void createResourceCrd(KubernetesClient kubClient, CustomResourceDefinition crd, String resourceType) {

        try {
            kubClient.apiextensions().v1().customResourceDefinitions().createOrReplace(crd);
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

    public static <T extends CustomResource> ResourceEventHandler resourceEventHandlerFor(
            ResourceClient<T> resourceClient,
            ResourceEventHandler<T> handler,
            DefaultWatchManager.EventStorage<DefaultWatchManager.DispatcherEvent> eventStorage) {

        return resourceEventHandlerFor(
                handler,
                resourceClient.getResourceType(),
                resourceClient.getCustomResourceDefinition(),
                eventStorage);
    }


    public static <T extends CustomResource> ResourceEventHandler<T> resourceEventHandlerFor(
            ResourceEventHandler<T> eventHandler,
            Class<T> resourceType,
            CustomResourceDefinition crd,
            DefaultWatchManager.EventStorage<DefaultWatchManager.DispatcherEvent> eventStorage) {
        CustomResourceDefinitionSpec spec = crd.getSpec();

        String apiVersion = spec.getGroup() + "/" + spec.getVersions().get(0);
        String kind = spec.getNames().getKind();

        KubernetesDeserializer.registerCustomKind(apiVersion, kind, resourceType);

        return new GenericResourceEventHandler<>(eventHandler, eventStorage);
    }
}
