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

package com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.exactpro.th2.infraoperator.fabric8.util.CustomResourceUtils.getResourceCrd;
import static com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils.extractName;


@Data
public abstract class DefaultResourceClient<CR extends CustomResource> implements ResourceClient<CR> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultResourceClient.class);


    private final KubernetesClient kubClient;

    private final Class<CR> resourceType;

    private final CustomResourceDefinition customResourceDefinition;

    private final MixedOperation<CR, ? extends KubernetesResourceList<CR>, ? extends Doneable<CR>, ? extends Resource<CR, ? extends Doneable<CR>>> instance;


    public DefaultResourceClient(
            KubernetesClient client,
            Class<CR> resourceType,
            Class<? extends KubernetesResourceList<CR>> listClass,
            Class<? extends Doneable<CR>> doneClass,
            String crdName
    ) {
        this.kubClient = client;
        this.resourceType = resourceType;
        this.customResourceDefinition = getResourceCrd(client, crdName);
        this.instance = kubClient.customResources(customResourceDefinition, resourceType, listClass, doneClass);

        client.customResourceDefinitions().withName(crdName).watch(new CRDWatcher());
        logger.info("Started watching for custom resource definition [CRD<{}>]", crdName);
    }


    @Override
    public Class<CR> getResourceType() {
        return resourceType;
    }

    @Override
    public CustomResourceDefinition getCustomResourceDefinition() {
        return customResourceDefinition;
    }


    private static class CRDWatcher implements Watcher<CustomResourceDefinition> {

        @Override
        public void eventReceived(Action action, CustomResourceDefinition crd) {

            switch (action) {
                case MODIFIED:
                case DELETED:
                case ERROR:
                    logger.error("[CRD<{}>] has been changed. Operator will shutdown...", extractName(crd));
                    System.exit(1);
            }

        }

        @Override
        public void onClose(KubernetesClientException cause) {
            if (cause != null) {
                logger.error("Watcher has been closed cause: {}", cause.getMessage(), cause);
            }
        }

    }
}

