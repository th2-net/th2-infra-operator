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

package com.exactpro.th2.infraoperator.operator;

import com.exactpro.th2.infraoperator.Th2CrdController;
import com.exactpro.th2.infraoperator.model.http.HttpCode;
import com.exactpro.th2.infraoperator.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.RetryableTaskQueue;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.tasks.TriggerRedeployTask;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.fabric8.kubernetes.client.Watcher.Action.DELETED;
import static io.fabric8.kubernetes.client.Watcher.Action.MODIFIED;

public abstract class AbstractTh2Operator<CR extends Th2CustomResource, KO extends HasMetadata> implements Watcher<CR> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTh2Operator.class);

    private static final int REDEPLOY_DELAY = 120;

    public static final String REFRESH_TOKEN_ALIAS = "refresh-token";

    public static final String ANTECEDENT_LABEL_KEY_ALIAS = "th2.exactpro.com/antecedent";

    private final RetryableTaskQueue retryableTaskQueue = new RetryableTaskQueue();


    protected Watch kubObjWatch;
    protected final Set<String> targetCrFullNames;
    protected final KubernetesClient kubClient;
    protected final Map<String, CR> bunches;


    protected AbstractTh2Operator(KubernetesClient kubClient) {
        this.kubClient = kubClient;
        this.bunches = new ConcurrentHashMap<>();
        this.targetCrFullNames = ConcurrentHashMap.newKeySet();
    }


    @Override
    public void eventReceived(Action action, CR resource) {

        logger.debug("Received {} event for \"{}\"", action, CustomResourceUtils.annotationFor(resource));
        String resourceType = ExtractUtils.extractType(resource);

        var resFullName = ExtractUtils.extractFullName(resource);

        try {

            var existRes = bunches.get(resFullName);

            bunches.put(resFullName, resource);

            if (action.equals(MODIFIED)
                    && Objects.nonNull(existRes)
                    && ExtractUtils.compareRefreshTokens(existRes, resource)
                    && ExtractUtils.extractGeneration(existRes).equals(ExtractUtils.extractGeneration(resource))) {
                logger.debug("Received {} event for \"{}\", but no changes detected and exiting", action, CustomResourceUtils.annotationFor(resource));

                return;
            }

            processEvent(action, resource);

        } catch (Exception e) {
            logger.error("Something went wrong while processing [{}] event of [{}<{}>]",
                    action, resourceType, resFullName, e);

            resource.getStatus().failed(e);
            updateStatus(resource);

            //create and schedule task to redeploy failed component
            TriggerRedeployTask triggerRedeployTask = new TriggerRedeployTask(this, getResourceClient(), kubClient, resource, action, REDEPLOY_DELAY);
            retryableTaskQueue.add(triggerRedeployTask, true);

            logger.info("added task \"{}\" to scheduler, with delay \"{}\" seconds", triggerRedeployTask.getName(), REDEPLOY_DELAY);
        }

    }

    @Override
    public void onClose(KubernetesClientException cause) {

        if (cause != null)
            logger.error("Watcher[1] has been closed for {}", this.getClass().getSimpleName(), cause);
    }


    public abstract ResourceClient<CR> getResourceClient();


    @SneakyThrows
    protected KO loadKubObj(String kubObjDefPath) {

        try (var somePodYml = Th2CrdController.class.getResourceAsStream(kubObjDefPath)) {

            var ko = parseStreamToKubObj(somePodYml);

            String kubObjType = ko.getClass().getSimpleName();

            logger.info("[{}] from '{}' has been loaded", kubObjType, kubObjDefPath);

            return ko;
        }

    }

    @SuppressWarnings("unchecked")
    protected KO parseStreamToKubObj(InputStream stream) {
        return (KO) kubClient.load(stream).get().get(0);
    }

    protected void processEvent(Action action, CR resource) {

        logger.debug("Processing event {} for \"{}\"", action, CustomResourceUtils.annotationFor(resource));

        resource.getStatus().idle();

        resource = updateStatus(resource);


        var resFullName = ExtractUtils.extractFullName(resource);

        startWatchForKubObj(resource);


        switch (action) {
            case ADDED:
                logger.info("Resource [{}] has been added", resFullName);

                resource.getStatus().installing();

                resource = updateStatus(resource);

                addedEvent(resource);

                break;
            case MODIFIED:
                logger.info("Resource [{}] has been modified", resFullName);

                resource.getStatus().upgrading();

                resource = updateStatus(resource);

                modifiedEvent(resource);

                break;
            case DELETED:
                logger.info("Resource [{}] has been deleted", resFullName);

                deletedEvent(resource);

                break;
            case ERROR:
                logger.warn("Some error occurred while processing [{}]", resFullName);

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

        bunches.remove(ExtractUtils.extractFullName(resource));

    }

    protected void errorEvent(CR resource) {
        bunches.remove(ExtractUtils.extractFullName(resource));
    }


    protected CR updateStatus(CR resource) {

        var resFullName = ExtractUtils.extractFullName(resource);

        var resClient = getResourceClient().getInstance();

        try {
            return resClient.inNamespace(ExtractUtils.extractNamespace(resource)).updateStatus(resource);
        } catch (KubernetesClientException e) {

            if (HttpCode.ofCode(e.getCode()) == HttpCode.SERVER_CONFLICT) {
                logger.warn("Failed to update status of [{}] resource to [{}] because it has been " +
                        "already changed on the server. Trying to sync a resource...", resFullName, resource.getStatus().getPhase());
                var freshRes = resClient.inNamespace(ExtractUtils.extractNamespace(resource)).list().getItems().stream()
                        .filter(r -> ExtractUtils.extractName(r).equals(ExtractUtils.extractName(resource)))
                        .findFirst()
                        .orElse(null);
                if (Objects.nonNull(freshRes)) {
                    freshRes.setStatus(resource.getStatus());
                    var updatedRes = updateStatus(freshRes);
                    bunches.put(resFullName, updatedRes);
                    logger.info("Status of [{}] resource successfully updated to [{}]", resFullName, resource.getStatus().getPhase());
                    return updatedRes;
                } else {
                    logger.warn("Unable to update status of [{}] resource to [{}]: resource not present", resFullName, resource.getStatus().getPhase());
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

        resource.getStatus().succeeded(kubObjType + " successfully deployed", ExtractUtils.extractName(kubObj));

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

    protected void startWatchForKubObj(CR resource) {
        targetCrFullNames.add(ExtractUtils.extractFullName(resource));
        synchronized (this) {
            if (kubObjWatch == null) {
                kubObjWatch = setKubObjWatcher(ExtractUtils.extractNamespace(resource), new KubObjWatcher());
            }
        }
    }

    protected void mapProperties(CR resource, KO kubObj) {

        var kubObjMD = kubObj.getMetadata();

        var resMD = resource.getMetadata();

        var resName = resMD.getName();

        var resNamespace = ExtractUtils.extractNamespace(resource);


        kubObjMD.setName(resName);

        kubObjMD.setNamespace(ExtractUtils.extractNamespace(resource));

        kubObjMD.setLabels(resMD.getLabels());

        kubObjMD.setAnnotations(resMD.getAnnotations());

        kubObjMD.setAnnotations(kubObjMD.getAnnotations() != null ? kubObjMD.getAnnotations() : new HashMap<>());

        String resourceType = ExtractUtils.extractType(resource);

        kubObjMD.getAnnotations().put(ANTECEDENT_LABEL_KEY_ALIAS, resNamespace + ":" + resourceType + "/" + resName);

    }

    protected OwnerReference createOwnerReference(CR resource) {
        return new OwnerReferenceBuilder()
                .withKind(resource.getKind())
                .withName(ExtractUtils.extractName(resource))
                .withApiVersion(resource.getApiVersion())
                .withUid(resource.getMetadata().getUid())
                .withBlockOwnerDeletion(true)
                .build();
    }

    protected boolean isResourceExist(String resourceFullName) {
        return Objects.nonNull(bunches.get(resourceFullName));
    }


    protected abstract String getKubObjDefPath(CR resource);

    protected abstract void createKubObj(String namespace, KO kubObj);

    protected abstract Watch setKubObjWatcher(String namespace, Watcher<KO> objWatcher);


    protected class KubObjWatcher implements Watcher<KO> {

        @Override
        public void eventReceived(Action action, KO kubObj) {

            String resourceLabel = CustomResourceUtils.annotationFor(kubObj);
            logger.debug("Received {} event for \"{}\"", action, resourceLabel);

            String currentOwnerFullName = ExtractUtils.extractOwnerFullName(kubObj);
            if (Objects.isNull(currentOwnerFullName) || !targetCrFullNames.contains(currentOwnerFullName))
                return;

            logger.debug("Processing {} event for \"{}\"", action, resourceLabel);

            if (action.equals(DELETED)) {
                logger.info("\"{}\" has been deleted. Trying to redeploy", resourceLabel);

                if (isResourceExist(currentOwnerFullName)) {

                    String namepace = kubObj.getMetadata().getNamespace();
                    Namespace namespaceObj = kubClient.namespaces().withName(namepace).get();
                    if (namespaceObj == null || !namespaceObj.getStatus().getPhase().equals("Active")) {
                        logger.info("Namespace \"{}\" deleted or not active, cancelling", namepace);
                        return;
                    }

                    ObjectMeta kubObjMD = kubObj.getMetadata();
                    kubObjMD.setUid(null);
                    kubObjMD.setResourceVersion(null);
                    createKubObj(namepace, kubObj);

                    logger.info("\"{}\" has been redeployed", resourceLabel);

                } else
                    logger.warn("Owner for \"{}\" not found", resourceLabel);
            }

        }

        @Override
        public void onClose(KubernetesClientException cause) {
            if (cause != null)
                logger.error("Watcher[2] has been closed for {}", this.getClass().getSimpleName(), cause);
        }

    }

}
