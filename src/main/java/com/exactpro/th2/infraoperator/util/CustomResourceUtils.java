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

import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.model.kubernetes.client.ResourceClient;
import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionSpec;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;


public final class CustomResourceUtils {

    private static final String DEFAULT_NAMESPACE = "default";
    private static final Logger logger = LoggerFactory.getLogger(CustomResourceUtils.class);

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


    public static <T extends CustomResource> Watch watchFor(ResourceClient<T> resourceClient, Watcher<T> watcher) {

        return watchFor(
                watcher,
                resourceClient.getResourceType(),
                resourceClient.getCustomResourceDefinition(),
                resourceClient.getInstance()
        );
    }


    private static class RecoveringWatch<T extends CustomResource, L extends KubernetesResourceList<T>, D extends Doneable<T>> implements Watcher<T>, Watch {

        private Watch watch;
        private String kind;
        private Watcher<T> watcher;
        MixedOperation<T, L, D, ? extends Resource<T, ? extends Doneable<T>>> crClient;


        public RecoveringWatch(
                Watcher<T> watcher,
                Class<T> resourceType,
                CustomResourceDefinition crd,
                MixedOperation<T, L, D, ? extends Resource<T, ? extends Doneable<T>>> crClient
        ) {

            this.watcher = watcher;
            this.crClient = crClient;

            CustomResourceDefinitionSpec spec = crd.getSpec();

            /*
                Multiple versions in CRD specs in new lib
                TODO: please check if getting the first version will suffice
            */

            String apiVersion = spec.getGroup() + "/" + spec.getVersions().get(0);
            kind = spec.getNames().getKind();

            KubernetesDeserializer.registerCustomKind(apiVersion, kind, resourceType);
        }


        public Watch watch() {
            watch = crClient.inAnyNamespace().watch(this);
            logger.info("Started watching for \"{}\" resources", kind);
            return watch;
        }

        @Override
        public void close() {
            watch.close();
            logger.info("Closed watch for \"{}\"", kind);
        }

        @Override
        public void eventReceived(Action action, T resource) {
            LocalDateTime startDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);

            String namespace = resource.getMetadata().getNamespace();
            List<String> namespacePrefixes = OperatorConfig.INSTANCE.getNamespacePrefixes();
            if (namespace != null
                    && namespacePrefixes != null
                    && namespacePrefixes.size() > 0
                    && namespacePrefixes.stream().noneMatch(namespace::startsWith)) {
                return;
            }

            try {
                watcher.eventReceived(action, resource);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }

            LocalDateTime endDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
            Duration duration = Duration.between(startDateTime, endDateTime);
            logger.info("{} Event for {} processed in {}ms",
                    action.toString(),
                    annotationFor(resource),
                    duration.toMillis());
        }

        @Override
        public void onClose(KubernetesClientException cause) {
            watcher.onClose(cause);
            if (cause != null) {
                logger.error("Exception watching for \"{}\" resources", kind, cause);
                watch();
            }
        }
    }


    public static <T extends CustomResource, L extends KubernetesResourceList<T>, D extends Doneable<T>> Watch watchFor(
            Watcher<T> watcher,
            Class<T> resourceType,
            CustomResourceDefinition crd,
            MixedOperation<T, L, D, ? extends Resource<T, ? extends Doneable<T>>> crClient
    ) {
        CustomResourceDefinitionSpec spec = crd.getSpec();

        /*
            Multiple versions in CRD specs in new lib
            TODO: please check if getting the first version will suffice
         */

        String apiVersion = spec.getGroup() + "/" + spec.getVersions().get(0);
        String kind = spec.getNames().getKind();

        KubernetesDeserializer.registerCustomKind(apiVersion, kind, resourceType);

        RecoveringWatch<T, L, D> watch = new RecoveringWatch<>(watcher, resourceType, crd, crClient);
        watch.watch();
        return watch;
    }

}
