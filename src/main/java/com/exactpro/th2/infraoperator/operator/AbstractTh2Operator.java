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

package com.exactpro.th2.infraoperator.operator;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.Th2CrdController;
import com.exactpro.th2.infraoperator.metrics.OperatorMetrics;
import com.exactpro.th2.infraoperator.model.http.HttpCode;
import com.exactpro.th2.infraoperator.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.NonTerminalException;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.RetryableTaskQueue;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.tasks.TriggerRedeployTask;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.ExtractUtils;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.prometheus.client.Histogram;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;
import static io.fabric8.kubernetes.client.Watcher.Action.MODIFIED;

public abstract class AbstractTh2Operator<CR extends Th2CustomResource, KO extends HasMetadata> implements Watcher<CR> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTh2Operator.class);

    private static final int REDEPLOY_DELAY = 120;

    public static final String REFRESH_TOKEN_ALIAS = "refresh-token";

    public static final String ANTECEDENT_LABEL_KEY_ALIAS = "th2.exactpro.com/antecedent";

    private final RetryableTaskQueue retryableTaskQueue = new RetryableTaskQueue();

    protected final KubernetesClient kubClient;

    private final Map<String, ResourceFingerprint> fingerprints;

    protected AbstractTh2Operator(KubernetesClient kubClient) {
        this.kubClient = kubClient;
        this.fingerprints = new ConcurrentHashMap<>();
    }

    @Override
    public void eventReceived(Action action, CR resource) {
        String resourceLabel = CustomResourceUtils.annotationFor(resource);

        try {
            var cachedFingerprint = fingerprints.get(resourceLabel);
            var resourceFingerprint = new ResourceFingerprint(resource);

            if (cachedFingerprint != null && action.equals(MODIFIED)
                    && cachedFingerprint.equals(resourceFingerprint)) {
                logger.debug("No changes detected for \"{}\"", resourceLabel);
                return;
            }
            Histogram.Timer processTimer = OperatorMetrics.getCustomResourceEventTimer(resource);
            try {
                logger.debug("refresh-token={}", resourceFingerprint.refreshToken);

                CustomResourceUtils.removeDuplicatedPins(resource);

                processEvent(action, resource);

            } catch (NonTerminalException e) {
                logger.error("Non-terminal Exception processing {} event for \"{}\". Will try to redeploy.",
                        action, resourceLabel, e);

                String namespace = resource.getMetadata().getNamespace();
                Namespace namespaceObj = kubClient.namespaces().withName(namespace).get();
                if (namespaceObj == null || !namespaceObj.getStatus().getPhase().equals("Active")) {
                    logger.info("Namespace \"{}\" deleted or not active, cancelling", namespace);
                    return;
                }

                resource.getStatus().failed(e.getMessage());
                updateStatus(resource);

                //create and schedule task to redeploy failed component
                TriggerRedeployTask triggerRedeployTask = new TriggerRedeployTask(this,
                        getResourceClient(), kubClient, resource, action, REDEPLOY_DELAY);
                retryableTaskQueue.add(triggerRedeployTask, true);

                logger.info("Task \"{}\" added to scheduler, with delay \"{}\" seconds",
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
            Namespace namespaceObj = kubClient.namespaces().withName(namespace).get();
            if (namespaceObj == null || !namespaceObj.getStatus().getPhase().equals("Active")) {
                logger.info("Namespace \"{}\" deleted or not active, cancelling", namespace);
                return;
            }
            resource.getStatus().failed(e.getMessage());
            updateStatus(resource);
            logger.error("Terminal Exception processing {} event for {}. Will not try to redeploy",
                    action, resourceLabel, e);
        }
    }

    public abstract ResourceClient<CR> getResourceClient();

    @SneakyThrows
    protected KO loadKubObj(String kubObjDefPath) {

        try (var somePodYml = Th2CrdController.class.getResourceAsStream(kubObjDefPath)) {

            var ko = parseStreamToKubObj(somePodYml);
            String kubObjType = ko.getClass().getSimpleName();
            logger.info("{} from \"{}\" has been loaded", kubObjType, kubObjDefPath);
            return ko;
        }

    }

    @SuppressWarnings("unchecked")
    protected KO parseStreamToKubObj(InputStream stream) {
        return (KO) kubClient.load(stream).get().get(0);
    }

    protected void processEvent(Action action, CR resource) {

        String resourceLabel = CustomResourceUtils.annotationFor(resource);
        logger.debug("Processing event {} for \"{}\"", action, resourceLabel);

        switch (action) {
            case ADDED:
                resource.getStatus().installing();
                resource = updateStatus(resource);
                addedEvent(resource);
                logger.info("Resource \"{}\" has been added", resourceLabel);
                break;

            case MODIFIED:
                resource.getStatus().upgrading();
                resource = updateStatus(resource);
                modifiedEvent(resource);
                logger.info("Resource \"{}\" has been modified", resourceLabel);
                break;

            case DELETED:
                deletedEvent(resource);
                logger.info("Resource \"{}\" has been deleted", resourceLabel);
                break;

            case ERROR:
                logger.warn("Error while processing \"{}\"", resourceLabel);
                resource.getStatus().failed("Unknown error from kubernetes");
                resource = updateStatus(resource);
                errorEvent(resource);
                break;
        }
    }

    protected void addedEvent(CR resource) {
        setupAndCreateKubObj(resource);
    }

    protected void modifiedEvent(CR resource) {
        setupAndCreateKubObj(resource);
    }

    protected void deletedEvent(CR resource) {

        // kubernetes objects will be removed when custom resource removed (through 'OwnerReference')

        String resourceLabel = CustomResourceUtils.annotationFor(resource);
        fingerprints.remove(resourceLabel);
        OperatorState.INSTANCE.removeHelmReleaseFromCache(extractName(resource), extractNamespace(resource));
    }

    protected void errorEvent(CR resource) {
        String resourceLabel = CustomResourceUtils.annotationFor(resource);
        fingerprints.remove(resourceLabel);
    }

    protected CR updateStatus(CR resource) {

        String resourceLabel = CustomResourceUtils.annotationFor(resource);
        var resClient = getResourceClient().getInstance();

        try {
            return resClient.inNamespace(ExtractUtils.extractNamespace(resource)).replaceStatus(resource);
        } catch (KubernetesClientException e) {

            if (HttpCode.ofCode(e.getCode()) == HttpCode.SERVER_CONFLICT) {
                logger.warn("Failed to update status for \"{}\"  to \"{}\" because it has been " +
                                "already changed on the server. Trying to sync a resource...",
                        resourceLabel, resource.getStatus().getPhase());
                var freshRes = resClient.inNamespace(ExtractUtils.extractNamespace(resource)).list().getItems().stream()
                        .filter(r -> extractName(r).equals(extractName(resource)))
                        .findFirst()
                        .orElse(null);
                if (Objects.nonNull(freshRes)) {
                    freshRes.setStatus(resource.getStatus());
                    var updatedRes = updateStatus(freshRes);
                    fingerprints.put(resourceLabel, new ResourceFingerprint(updatedRes));
                    logger.info("Status for \"{}\" resource successfully updated to \"{}\"",
                            resourceLabel, resource.getStatus().getPhase());
                    return updatedRes;
                } else {
                    logger.warn("Unable to update status for \"{}\" resource to \"{}\": resource not present",
                            resourceLabel, resource.getStatus().getPhase());
                    return resource;
                }
            }

            throw e;
        }
    }

    protected void setupAndCreateKubObj(CR resource) {

        var kubObj = loadKubObj(getKubObjDefPath(resource));

        setupKubObj(resource, kubObj);

        createKubObj(ExtractUtils.extractNamespace(resource), kubObj);

        logger.info("Generated \"{}\" based on \"{}\""
                , CustomResourceUtils.annotationFor(kubObj)
                , CustomResourceUtils.annotationFor(resource));

        String kubObjType = kubObj.getClass().getSimpleName();

        resource.getStatus().succeeded(kubObjType + " successfully deployed", extractName(kubObj));

        updateStatus(resource);
    }

    protected void setupKubObj(CR resource, KO kubObj) {

        mapProperties(resource, kubObj);

        logger.info("Generated additional properties from \"{}\" for the resource \"{}\""
                , CustomResourceUtils.annotationFor(resource)
                , CustomResourceUtils.annotationFor(kubObj));

        kubObj.getMetadata().setOwnerReferences(List.of(createOwnerReference(resource)));

        logger.info("Property \"OwnerReference\" with reference to \"{}\" has been set for the resource \"{}\""
                , CustomResourceUtils.annotationFor(resource)
                , CustomResourceUtils.annotationFor(kubObj));

    }

    protected void mapProperties(CR resource, KO kubObj) {

        var kubObjMD = kubObj.getMetadata();
        var resMD = resource.getMetadata();
        String resName = resMD.getName();
        String annotation = CustomResourceUtils.annotationFor(resource);
        String finalName = CustomResourceUtils.extractHashedName(resource);

        if (!finalName.equals(resName)) {
            logger.info("Name of resource \"{}\" exceeds limitations. Will be substituted with \"{}\"",
                    annotation, finalName);
        }

        kubObjMD.setName(finalName);
        kubObjMD.setNamespace(ExtractUtils.extractNamespace(resource));
        kubObjMD.setLabels(resMD.getLabels());
        kubObjMD.setAnnotations(resMD.getAnnotations());
        kubObjMD.setAnnotations(kubObjMD.getAnnotations() != null ? kubObjMD.getAnnotations() : new HashMap<>());

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

    protected abstract String getKubObjDefPath(CR resource);

    protected abstract void createKubObj(String namespace, KO kubObj);

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
