/*
 * Copyright 2020-2024 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.infraoperator.operator;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.configuration.ConfigLoader;
import com.exactpro.th2.infraoperator.metrics.OperatorMetrics;
import com.exactpro.th2.infraoperator.model.box.mq.factory.MessageRouterConfigFactory;
import com.exactpro.th2.infraoperator.model.http.HttpCode;
import com.exactpro.th2.infraoperator.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.NonTerminalException;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.RetryableTaskQueue;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.tasks.TriggerRedeployTask;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.prometheus.client.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.ANTECEDENT_LABEL_KEY_ALIAS;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.extractHashedName;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;
import static com.exactpro.th2.infraoperator.util.KubernetesUtils.isNotActive;
import static io.fabric8.kubernetes.client.Watcher.Action.MODIFIED;

public abstract class AbstractTh2Operator<CR extends Th2CustomResource> implements Watcher<CR> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTh2Operator.class);

    private static final int REDEPLOY_DELAY = 120;

    public static final String REFRESH_TOKEN_ALIAS = "refresh-token";

    private final RetryableTaskQueue retryableTaskQueue = new RetryableTaskQueue();

    protected final KubernetesClient kubClient;

    private final Map<String, ResourceFingerprint> fingerprints;

    protected AbstractTh2Operator(KubernetesClient kubClient) {
        this.kubClient = kubClient;
        this.fingerprints = new ConcurrentHashMap<>();
    }

    @Override
    public void eventReceived(Action action, CR resource) {
        String resourceLabel = annotationFor(resource);

        try {
            var cachedFingerprint = fingerprints.get(resourceLabel);
            var resourceFingerprint = new ResourceFingerprint(resource);

            if (cachedFingerprint != null && action.equals(MODIFIED)
                    && cachedFingerprint.equals(resourceFingerprint)) {
                LOGGER.debug("No changes detected for \"{}\"", resourceLabel);
                return;
            }
            Histogram.Timer processTimer = OperatorMetrics.getCustomResourceEventTimer(resource);
            try {
                LOGGER.debug("refresh-token={}", resourceFingerprint.refreshToken);

                processEvent(action, resource);

            } catch (NonTerminalException e) {
                LOGGER.error("Non-terminal Exception processing {} event for \"{}\". Will try to redeploy.",
                        action, resourceLabel, e);

                String namespace = resource.getMetadata().getNamespace();
                if (isNotActive(kubClient, namespace)) {
                    LOGGER.info("Namespace \"{}\" deleted or not active, cancelling", namespace);
                    return;
                }

                resource.getStatus().failed(e.getMessage());
                updateStatus(resource);

                //create and schedule task to redeploy failed component
                TriggerRedeployTask triggerRedeployTask = new TriggerRedeployTask(
                        this,
                        getResourceClient(),
                        kubClient,
                        resource,
                        action,
                        REDEPLOY_DELAY
                );
                retryableTaskQueue.add(triggerRedeployTask, true);

                LOGGER.info("Task \"{}\" added to scheduler, with delay \"{}\" seconds",
                        triggerRedeployTask.getName(), REDEPLOY_DELAY);
            } finally {
                fingerprints.put(resourceLabel, resourceFingerprint);
                //observe event processing time for only operator
                processTimer.observeDuration();
                //observe time it took to process event only by both manager and operator
                OperatorMetrics.observeTotal(resource);
            }

        } catch (Exception e) {
            String namespace = resource.getMetadata().getNamespace();
            if (isNotActive(kubClient, namespace)) {
                LOGGER.info("Namespace \"{}\" deleted or not active, cancelling", namespace);
                return;
            }
            resource.getStatus().failed(e.getMessage());
            updateStatus(resource);
            LOGGER.error("Terminal Exception processing {} event for {}. Will not try to redeploy",
                    action, resourceLabel, e);
        }
    }

    public abstract ResourceClient<CR> getResourceClient();

    protected HelmRelease loadKubObj() {
            var helmRelease = new HelmRelease();
            helmRelease.getSpec().setChart(ConfigLoader.getConfig().getChart());
//TODO restore this when moved to helm controller
//            helmRelease.getSpec().setTimeout(ConfigLoader.getConfig().getReleaseTimeout());
        return helmRelease;
    }

    protected void processEvent(Action action, CR resource) throws IOException {

        String resourceLabel = annotationFor(resource);
        LOGGER.debug("Processing event {} for \"{}\"", action, resourceLabel);

        if (resource.getSpec().getDisabled()) {
            try {
                deletedEvent(resource);
                // TODO: work with Th2CustomResource should be encapsulated somewhere
                //        to void issues with choosing the right function to extract the name
                String namespace = extractNamespace(resource);
                String helmReleaseName = extractHashedName(resource);
                Resource<HelmRelease> helmReleaseResource = kubClient.resources(HelmRelease.class)
                        .inNamespace(namespace)
                        // name must be hashed if it exceeds the limit
                        .withName(helmReleaseName);
                HelmRelease helmRelease = helmReleaseResource.get();
                if (helmRelease == null) {
                    LOGGER.info("Resource \"{}\" hasn't been deleted because it already doesn't exist",
                            annotationFor(namespace, helmReleaseName, HelmRelease.class.getSimpleName()));
                } else {
                    String helmReleaseLabel = annotationFor(helmRelease);
                    helmReleaseResource.delete();
                    LOGGER.info("Resource \"{}\" has been deleted", helmReleaseLabel);
                }
                resource.getStatus().disabled("Resource has been disabled");
                LOGGER.info("Resource \"{}\" has been disabled, executing DELETE action", resourceLabel);
                updateStatus(resource);
            } catch (Exception e) {
                resource.getStatus().failed("Unknown error");
                updateStatus(resource);
                LOGGER.error("Exception while processing disable feature for: \"{}\"", resourceLabel, e);
            }
            return;
        }

        switch (action) {
            case ADDED:
                resource.getStatus().installing();
                resource = updateStatus(resource);
                addedEvent(resource);
                LOGGER.info("Resource \"{}\" has been added", resourceLabel);
                break;

            case MODIFIED:
                resource.getStatus().upgrading();
                resource = updateStatus(resource);
                modifiedEvent(resource);
                LOGGER.info("Resource \"{}\" has been modified", resourceLabel);
                break;

            case DELETED:
                deletedEvent(resource);
                LOGGER.info("Resource \"{}\" has been deleted", resourceLabel);
                break;

            case ERROR:
                LOGGER.warn("Error while processing \"{}\"", resourceLabel);
                resource.getStatus().failed("Unknown error from kubernetes");
                resource = updateStatus(resource);
                errorEvent(resource);
                break;
        }
    }

    protected void addedEvent(CR resource) throws IOException {
        setupAndCreateKubObj(resource);
    }

    protected void modifiedEvent(CR resource) throws IOException {
        setupAndCreateKubObj(resource);
    }

    protected void deletedEvent(CR resource) {

        // kubernetes objects will be removed when custom resource removed (through 'OwnerReference')

        String resourceLabel = annotationFor(resource);
        fingerprints.remove(resourceLabel);
        // The HelmRelease name is hashed if Th2CustomResource name exceeds the limit
        OperatorState.INSTANCE.removeHelmReleaseFromCache(
                extractHashedName(resource),
                extractNamespace(resource)
        );
    }

    protected void errorEvent(CR resource) {
        String resourceLabel = annotationFor(resource);
        fingerprints.remove(resourceLabel);
    }

    protected CR updateStatus(CR resource) {

        String resourceLabel = annotationFor(resource);
        var resClient = getResourceClient().getInstance();

        try {
            var res = resClient.inNamespace(extractNamespace(resource)).resource(resource);
            return res.replaceStatus();
        } catch (KubernetesClientException e) {

            if (HttpCode.ofCode(e.getCode()) == HttpCode.SERVER_CONFLICT) {
                LOGGER.warn("Failed to update status for \"{}\"  to \"{}\" because it has been " +
                                "already changed on the server. Trying to sync a resource...",
                        resourceLabel, resource.getStatus().getPhase());
                var freshRes = resClient.inNamespace(extractNamespace(resource)).list().getItems().stream()
                        .filter(r -> extractName(r).equals(extractName(resource)))
                        .findFirst()
                        .orElse(null);
                if (Objects.nonNull(freshRes)) {
                    freshRes.setStatus(resource.getStatus());
                    var updatedRes = updateStatus(freshRes);
                    fingerprints.put(resourceLabel, new ResourceFingerprint(updatedRes));
                    LOGGER.info("Status for \"{}\" resource successfully updated to \"{}\"",
                            resourceLabel, resource.getStatus().getPhase());
                    return updatedRes;
                } else {
                    LOGGER.warn("Unable to update status for \"{}\" resource to \"{}\": resource not present",
                            resourceLabel, resource.getStatus().getPhase());
                    return resource;
                }
            }

            throw e;
        }
    }

    protected void setupAndCreateKubObj(CR resource) {

        var kubObj = loadKubObj();

        setupKubObj(resource, kubObj);

        createKubObj(extractNamespace(resource), kubObj);

        LOGGER.info("Generated \"{}\" based on \"{}\"" , annotationFor(kubObj) , annotationFor(resource));

        String kubObjType = kubObj.getClass().getSimpleName();

        resource.getStatus().succeeded(kubObjType + " successfully deployed", extractName(kubObj));

        updateStatus(resource);
    }

    protected void setupKubObj(CR resource, HelmRelease helmRelease) {

        mapProperties(resource, helmRelease);

        LOGGER.info("Generated additional properties from \"{}\" for the resource \"{}\""
                , annotationFor(resource)
                , annotationFor(helmRelease));

        helmRelease.getMetadata().setOwnerReferences(List.of(createOwnerReference(resource)));

        LOGGER.info("Property \"OwnerReference\" with reference to \"{}\" has been set for the resource \"{}\""
                , annotationFor(resource)
                , annotationFor(helmRelease));

    }

    protected void mapProperties(CR resource, HelmRelease helmRelease) {

        var kubObjMD = helmRelease.getMetadata();
        var resMD = resource.getMetadata();
        String resName = resMD.getName();
        String annotation = annotationFor(resource);
        String finalName = extractHashedName(resource);

        if (!finalName.equals(resName)) {
            LOGGER.info("Name of resource \"{}\" exceeds limitations. Will be substituted with \"{}\"",
                    annotation, finalName);
        }

        kubObjMD.setName(finalName);
        kubObjMD.setNamespace(extractNamespace(resource));
        kubObjMD.setLabels(resMD.getLabels());
        kubObjMD.setAnnotations(resMD.getAnnotations());
        kubObjMD.setAnnotations(kubObjMD.getAnnotations() == null ? new HashMap<>() : kubObjMD.getAnnotations());

        kubObjMD.getAnnotations().put(ANTECEDENT_LABEL_KEY_ALIAS, annotation);

    }

    protected OwnerReference createOwnerReference(CR resource) {
        return new OwnerReferenceBuilder()
                .withKind(resource.getKind())
                .withName(extractName(resource))
                .withApiVersion(resource.getApiVersion())
                .withUid(resource.getMetadata().getUid())
                .withBlockOwnerDeletion(true)
                .build();
    }

    protected abstract void createKubObj(String namespace, HelmRelease helmRelease);

    protected abstract MessageRouterConfigFactory getMqConfigFactory();

    @Override
    public void onClose(WatcherException cause) {
        throw new AssertionError("This method should not be called");
    }

    private static class ResourceFingerprint {
        private String refreshToken;

        private Long generation;

        public ResourceFingerprint(HasMetadata res) {

            var metadata = res.getMetadata();
            if (metadata == null) {
                return;
            }

            generation = res.getMetadata().getGeneration();

            var annotations = metadata.getAnnotations();
            if (annotations != null) {
                refreshToken = annotations.get(REFRESH_TOKEN_ALIAS);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ResourceFingerprint)) {
                return false;
            }

            ResourceFingerprint that = (ResourceFingerprint) o;
            return Objects.equals(refreshToken, that.refreshToken) &&
                    Objects.equals(generation, that.generation);
        }
    }
}
