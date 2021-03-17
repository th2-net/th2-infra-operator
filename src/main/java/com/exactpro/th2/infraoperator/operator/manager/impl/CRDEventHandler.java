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

package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.RESYNC_TIME;

public
class CRDEventHandler implements ResourceEventHandler<CustomResourceDefinition> {
    private static final Logger logger = LoggerFactory.getLogger(CRDEventHandler.class);

    public static CRDEventHandler newInstance(SharedInformerFactory sharedInformerFactory) {
        SharedIndexInformer<CustomResourceDefinition> crdInformer = sharedInformerFactory.sharedIndexInformerFor(
                CustomResourceDefinition.class, RESYNC_TIME);

        List<String> crdNames = List.of(
                "th2boxes.th2.exactpro.com",
                "th2coreboxes.th2.exactpro.com",
                "th2dictionaries.th2.exactpro.com",
                "th2estores.th2.exactpro.com",
                "th2links.th2.exactpro.com",
                "th2mstores.th2.exactpro.com");

        var res = new CRDEventHandler(crdNames);
        crdInformer.addEventHandler(res);
        return res;
    }

    private CRDEventHandler(List<String> crdNames) {
        if (crdNames == null) {
            logger.error("Can't initialize CRDResourceEventHandler, crdNames is null");
            return;
        }

        this.crdNames = crdNames;
    }

    private List<String> crdNames;

    private boolean notInCrdNames(String crdName) {
        return !(crdNames.stream().anyMatch(el -> el.equals(crdName)));
    }

    @Override
    public void onAdd(CustomResourceDefinition crd) {
        if (notInCrdNames(crd.getMetadata().getName())) {
            return;
        }

        logger.debug("Received ADDED event for \"{}\"", CustomResourceUtils.annotationFor(crd));
    }

    @Override
    public void onUpdate(CustomResourceDefinition oldCrd, CustomResourceDefinition newCrd) {
        if (notInCrdNames(oldCrd.getMetadata().getName())
                || oldCrd.getMetadata().getResourceVersion().equals(newCrd.getMetadata().getResourceVersion())) {
            return;
        }

        logger.info("CRD old ResourceVersion {}, new ResourceVersion {}",
                oldCrd.getMetadata().getResourceVersion(), newCrd.getMetadata().getResourceVersion());
        logger.error("Modification detected for CustomResourceDefinition \"{}\". going to shutdown...",
                oldCrd.getMetadata().getName());
        System.exit(1);
    }

    @Override
    public void onDelete(CustomResourceDefinition crd, boolean deletedFinalStateUnknown) {
        if (notInCrdNames(crd.getMetadata().getName())) {
            return;
        }
        logger.error("Modification detected for CustomResourceDefinition \"{}\". going to shutdown...",
                crd.getMetadata().getName());
        System.exit(1);
    }
}
