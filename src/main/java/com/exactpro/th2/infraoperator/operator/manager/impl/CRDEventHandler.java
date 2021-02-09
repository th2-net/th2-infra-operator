package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public
class CRDEventHandler implements ResourceEventHandler<CustomResourceDefinition> {
    private static final Logger logger = LoggerFactory.getLogger(CRDEventHandler.class);


    public CRDEventHandler (List<String> crdNames) {
        if (crdNames == null) {
            logger.error("Can't initialize CRDResourceEventHandler, crdNames is null");
            return;
        }

        this.crdNames = crdNames;
    }

    private List<String> crdNames;

    private boolean notInCrdNames (String crdName) {
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
                || oldCrd.getMetadata().getResourceVersion().equals(newCrd.getMetadata().getResourceVersion()))
            return;

        logger.info("CRD old ResourceVersion {}, new ResourceVersion {}", oldCrd.getMetadata().getResourceVersion(), newCrd.getMetadata().getResourceVersion());
        logger.error("Modification detected for CustomResourceDefinition \"{}\". going to shutdown...", oldCrd.getMetadata().getName());
        System.exit(1);
    }

    @Override
    public void onDelete(CustomResourceDefinition crd, boolean deletedFinalStateUnknown) {
        if (notInCrdNames(crd.getMetadata().getName()))
            return;

        logger.error("Modification detected for CustomResourceDefinition \"{}\". going to shutdown...", crd.getMetadata().getName());
        System.exit(1);
    }
}

