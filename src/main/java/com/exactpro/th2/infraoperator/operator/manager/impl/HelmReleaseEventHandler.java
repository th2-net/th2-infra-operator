package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.spec.helmRelease.HelmRelease;
import com.exactpro.th2.infraoperator.spec.helmRelease.HelmReleaseList;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.box.BoxResourceFinder;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.HELM_RELEASE_CRD_NAME;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.RESYNC_TIME;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;

// TODO, this class needs rework
public class HelmReleaseEventHandler implements Watcher<HelmRelease> {

    private static final Logger logger = LoggerFactory.getLogger(HelmReleaseEventHandler.class);

    private final KubernetesClient client;
    private final MixedOperation<HelmRelease, HelmReleaseList, Resource<HelmRelease>> helmReleaseClient;
    private final BoxResourceFinder resourceFinder;

    public static HelmReleaseEventHandler newInstance(
            SharedInformerFactory factory,
            KubernetesClient client,
            EventQueue eventQueue,
            BoxResourceFinder resourceFinder) {

        var res = new HelmReleaseEventHandler(client, resourceFinder);
        var helmReleaseCrd = CustomResourceUtils.getResourceCrd(client, HELM_RELEASE_CRD_NAME);

        SharedIndexInformer<HelmRelease> helmReleaseInformer = factory.sharedIndexInformerForCustomResource(
                new CustomResourceDefinitionContext.Builder()
                        .withGroup(helmReleaseCrd.getSpec().getGroup())
                        .withVersion(helmReleaseCrd.getSpec().getVersions().get(0).getName())
                        .withScope(helmReleaseCrd.getSpec().getScope())
                        .withPlural(helmReleaseCrd.getSpec().getNames().getPlural())
                        .build(),
                HelmRelease.class,
                HelmReleaseList.class,
                RESYNC_TIME);

        helmReleaseInformer.addEventHandler(CustomResourceUtils.resourceEventHandlerFor(
                res,
                HelmRelease.class,
                helmReleaseCrd,
                eventQueue));
        return res;
    }

    private HelmReleaseEventHandler(KubernetesClient client, BoxResourceFinder resourceFinder) {
        this.client = client;
        this.resourceFinder = resourceFinder;

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
    public void eventReceived(Action action, HelmRelease helmRelease) {
        if (action != Action.DELETED)
            return;
        String resourceLabel = annotationFor(helmRelease);
        String name = helmRelease.getMetadata().getName();
        String namespace = helmRelease.getMetadata().getNamespace();
        if(resourceFinder.getResource(name, namespace) == null){
            logger.info("\"{}\" Can't find associated CR, probably operator deleted it. it won't be redeployed!", resourceLabel);
            return;
        }

        logger.info("\"{}\" has been deleted. Trying to redeploy", resourceLabel);

        Namespace namespaceObj = client.namespaces().withName(namespace).get();
        if (namespaceObj == null || !namespaceObj.getStatus().getPhase().equals("Active")) {
            logger.info("Namespace \"{}\" deleted or not active, cancelling", namespace);
            return;
        }

        ObjectMeta kubObjMD = helmRelease.getMetadata();
        kubObjMD.setUid(null);
        kubObjMD.setResourceVersion(null);
        try {
            helmReleaseClient.inNamespace(namespace).create(helmRelease);
            logger.info("\"{}\" has been redeployed", resourceLabel);
        } catch (Exception e){
            var hr = helmReleaseClient.inNamespace(namespace).withName(name).get();
            if (hr != null) {
                logger.warn("Exception redeploying \"{}\": resource already exists", resourceLabel);
            } else {
                logger.error("Exception redeploying HelmRelease", e);
            }
        }
    }


    @Override
    public void onClose(WatcherException cause) {
        throw new AssertionError("This method should not be called");
    }

}