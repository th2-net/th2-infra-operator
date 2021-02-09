package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.spec.helmRelease.HelmRelease;
import com.exactpro.th2.infraoperator.spec.helmRelease.HelmReleaseList;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.exactpro.th2.infraoperator.operator.AbstractTh2Operator.ANTECEDENT_LABEL_KEY_ALIAS;
import static com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.HELM_RELEASE_CRD_NAME;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;

public class HelmReleaseEventHandler implements ResourceEventHandler<HelmRelease> {
    private static final Logger logger = LoggerFactory.getLogger(HelmReleaseEventHandler.class);

    private final KubernetesClient client;
    private final MixedOperation<HelmRelease, HelmReleaseList, Resource<HelmRelease>> helmReleaseClient;

    public HelmReleaseEventHandler (KubernetesClient client) {
        this.client = client;

        var helmReleaseCrd = CustomResourceUtils.getResourceCrd(client, HELM_RELEASE_CRD_NAME);

        CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                .withGroup(helmReleaseCrd.getSpec().getGroup())
                .withVersion(helmReleaseCrd.getSpec().getVersions().get(0).getName())
                .withScope(helmReleaseCrd.getSpec().getScope())
                .withPlural(helmReleaseCrd.getSpec().getNames().getPlural())
                .build();

        helmReleaseClient = client.customResources(
                crdContext,
                HelmRelease.class,
                HelmReleaseList.class
        );
    }

    @Override
    public void onAdd(HelmRelease helmRelease) {

    }

    @Override
    public void onUpdate(HelmRelease oldHelmRelease, HelmRelease newHelmRelease) {

    }

    @Override
    public void onDelete(HelmRelease helmRelease, boolean deletedFinalStateUnknown) {
        String resourceLabel = annotationFor(helmRelease);
        if (!helmRelease.getMetadata().getAnnotations().containsKey(ANTECEDENT_LABEL_KEY_ALIAS)) {
            logger.info("\"{}\" doesn't have ANTECEDENT annotation, probably operator deleted it. it won't be redeployed!", resourceLabel);

            return;
        }

        logger.info("\"{}\" has been deleted. Trying to redeploy", resourceLabel);

        String namespace = helmRelease.getMetadata().getNamespace();
        Namespace namespaceObj = client.namespaces().withName(namespace).get();
        if (namespaceObj == null || !namespaceObj.getStatus().getPhase().equals("Active")) {
            logger.info("Namespace \"{}\" deleted or not active, cancelling", namespace);
            return;
        }

        ObjectMeta kubObjMD = helmRelease.getMetadata();
        kubObjMD.setUid(null);
        kubObjMD.setResourceVersion(null);
        helmReleaseClient.inNamespace(namespace).createOrReplace(helmRelease);

        logger.info("\"{}\" has been redeployed", resourceLabel);
    }
}