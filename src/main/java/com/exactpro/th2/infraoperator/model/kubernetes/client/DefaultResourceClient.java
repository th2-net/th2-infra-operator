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

package com.exactpro.th2.infraoperator.model.kubernetes.client;

import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Data
public abstract class DefaultResourceClient<CR extends CustomResource> implements ResourceClient<CR> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultResourceClient.class);

    private final KubernetesClient client;
    private final Class<CR> resourceType;
    private final CustomResourceDefinition customResourceDefinition;
    private final MixedOperation<CR, ? extends KubernetesResourceList<CR>, ? extends Doneable<CR>, ? extends Resource<CR, ? extends Doneable<CR>>> instance;
    private final String crdName;

    private CRDWatcher watcher;

    public DefaultResourceClient(
            KubernetesClient client,
            Class<CR> resourceType,
            Class<? extends KubernetesResourceList<CR>> listClass,
            Class<? extends Doneable<CR>> doneClass,
            String crdName
    ) {
        this.client = client;
        this.resourceType = resourceType;
        this.customResourceDefinition = CustomResourceUtils.getResourceCrd(client, crdName);
        this.crdName = crdName;

        CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                .withGroup(customResourceDefinition.getSpec().getGroup())
                .withVersion(customResourceDefinition.getSpec().getVersions().get(0).getName())
                .withScope(customResourceDefinition.getSpec().getScope())
                .withPlural(customResourceDefinition.getSpec().getNames().getPlural())
                .build();

        instance = client.customResources(crdContext, resourceType, listClass, doneClass);

        watcher = new CRDWatcher();
        watcher.watch();
    }


    @Override
    public Class<CR> getResourceType() {
        return resourceType;
    }

    @Override
    public CustomResourceDefinition getCustomResourceDefinition() {
        return customResourceDefinition;
    }



    private class CRDWatcher implements Watcher<CustomResourceDefinition> {

        private void watch() {
            client.apiextensions().v1().customResourceDefinitions().withName(crdName).watch(watcher);
            logger.info("Watching for CustomResourceDefinition \"{}\"", crdName);
        }

        @Override
        public void eventReceived(Action action, CustomResourceDefinition crd) {

            logger.debug("Received {} event for \"{}\"", action, CustomResourceUtils.annotationFor(crd));
            if (action != Action.ADDED) {
                logger.error("Modification detected for CustomResourceDefinition \"{}\". going to shutdown...", crd.getMetadata().getName());
                System.exit(1);
            }
        }


        @Override
        public void onClose(KubernetesClientException cause) {
            if (cause != null) {
                logger.error("Exception watching CustomResourceDefinition {}", crdName, cause);
                watch();
            }
        }
    }

}

