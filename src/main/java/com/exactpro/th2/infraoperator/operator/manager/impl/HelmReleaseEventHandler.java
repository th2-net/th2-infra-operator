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

import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease;
import com.exactpro.th2.infraoperator.spec.strategy.resfinder.box.BoxResourceFinder;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.RESYNC_TIME;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;

// TODO: this class needs rework
public class HelmReleaseEventHandler implements Watcher<HelmRelease> {

    private static final Logger logger = LoggerFactory.getLogger(HelmReleaseEventHandler.class);

    private static final String FAKE_RESOURCE_ALIAS = "FakeCustomResource";

    private final KubernetesClient client;

    private final MixedOperation<HelmRelease, KubernetesResourceList<HelmRelease>, Resource<HelmRelease>>
            helmReleaseClient;

    private final BoxResourceFinder resourceFinder;

    public static HelmReleaseEventHandler newInstance(
            SharedInformerFactory factory,
            KubernetesClient client,
            EventQueue eventQueue,
            BoxResourceFinder resourceFinder) {

        var res = new HelmReleaseEventHandler(client, resourceFinder);

        SharedIndexInformer<HelmRelease> helmReleaseInformer = factory.sharedIndexInformerForCustomResource(
                HelmRelease.class, RESYNC_TIME);

        helmReleaseInformer.addEventHandler(new GenericResourceEventHandler<>(res, eventQueue));
        return res;
    }

    private HelmReleaseEventHandler(KubernetesClient client, BoxResourceFinder resourceFinder) {
        this.client = client;
        this.resourceFinder = resourceFinder;

        helmReleaseClient = client.customResources(HelmRelease.class);
    }

    @Override
    public void eventReceived(Action action, HelmRelease helmRelease) {
        if (action != Action.DELETED) {
            return;
        }
        String resourceLabel = annotationFor(helmRelease);
        var ownerReferences = helmRelease.getMetadata().getOwnerReferences();
        var ownerReference = ownerReferences.get(0);
        if (ownerReference == null) {
            logger.warn("Owner reference of resource \"{}\" is null. it won't be redeployed!",
                    resourceLabel);
            return;
        }
        String name = ownerReference.getName();
        String namespace = helmRelease.getMetadata().getNamespace();
        var resource = resourceFinder.getResource(name, namespace);
        if (resource == null || resource.getKind().equals(FAKE_RESOURCE_ALIAS)) {
            logger.info("\"{}\" Can't find associated CR, probably operator deleted it. it won't be redeployed!",
                    resourceLabel);
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
        } catch (Exception e) {
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
