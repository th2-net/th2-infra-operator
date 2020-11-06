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

package com.exactpro.th2.infraoperator.fabric8.operator;

import com.exactpro.th2.infraoperator.fabric8.Th2CrdController;
import com.exactpro.th2.infraoperator.fabric8.model.http.HttpCode;
import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.fabric8.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.VHostCreateException;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
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

import static com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils.*;
import static io.fabric8.kubernetes.client.Watcher.Action.DELETED;
import static io.fabric8.kubernetes.client.Watcher.Action.MODIFIED;

public abstract class AbstractTh2Operator<CR extends Th2CustomResource, KO extends HasMetadata> implements Watcher<CR> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTh2Operator.class);


    public static final String REFRESH_TOKEN_ALIAS = "refresh-token";

    public static final String ANTECEDENT_LABEL_KEY_ALIAS = "th2.exactpro.com/antecedent";


    protected String kubObjType;
    protected String resourceType;
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

        resourceType = extractType(resource);

        var resFullName = extractFullName(resource);

        try {

            var existRes = bunches.get(resFullName);

            bunches.put(resFullName, resource);

            if (action.equals(MODIFIED)
                    && Objects.nonNull(existRes)
                    && compareRefreshTokens(existRes, resource)
                    && extractGeneration(existRes).equals(extractGeneration(resource))) {
                return;
            }

            processEvent(action, resource);

        } catch (VHostCreateException e) {
            logger.error("Something went wrong while creating Vhost during processing [{}] event of [{}<{}>]",
                    action, resourceType, resFullName, e);

            resource.getStatus().failed(e.getMessage());

            updateStatus(resource);
        } catch (Exception e) {
            logger.error("Something went wrong while processing [{}] event of [{}<{}>]",
                    action, resourceType, resFullName, e);

            resource.getStatus().failed(e.getMessage());

            updateStatus(resource);
        }

    }

    @Override
    public void onClose(KubernetesClientException cause) {

        if (cause != null) {
            logger.error("Watcher has been closed cause: {}", cause.getMessage(), cause);
        }

        if (kubObjWatch != null) {
            kubObjWatch.close();
        }

    }


    public abstract ResourceClient<CR> getResourceClient();


    @SneakyThrows
    protected KO loadKubObj(String kubObjDefPath) {

        try (var somePodYml = Th2CrdController.class.getResourceAsStream(kubObjDefPath)) {

            var ko = parseStreamToKubObj(somePodYml);

            kubObjType = ko.getClass().getSimpleName();

            logger.info("[{}] from '{}' has been loaded", kubObjType, kubObjDefPath);

            return ko;
        }

    }

    @SuppressWarnings("unchecked")
    protected KO parseStreamToKubObj(InputStream stream) {
        return (KO) kubClient.load(stream).get().get(0);
    }

    protected void processEvent(Action action, CR resource) {
        debug("Received event '{}' for {}", action, resource);


        resource.getStatus().idle();

        resource = updateStatus(resource);


        var resFullName = extractFullName(resource);

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

        bunches.remove(extractFullName(resource));

    }

    protected void errorEvent(CR resource) {
        bunches.remove(extractFullName(resource));
    }


    protected CR updateStatus(CR resource) {

        var resFullName = extractFullName(resource);

        var resClient = getResourceClient().getInstance();

        try {
            return resClient.inNamespace(extractNamespace(resource)).updateStatus(resource);
        } catch (KubernetesClientException e) {

            if (HttpCode.ofCode(e.getCode()) == HttpCode.SERVER_CONFLICT) {
                logger.warn("Failed to update status of [{}] resource because it has been " +
                        "already changed on the server. Trying to sync a resource...", resFullName);
                var freshRes = resClient.inNamespace(extractNamespace(resource)).list().getItems().stream()
                        .filter(r -> extractName(r).equals(extractName(resource)))
                        .findFirst()
                        .orElse(null);
                if (Objects.nonNull(freshRes)) {
                    freshRes.setStatus(resource.getStatus());
                    var updatedRes = updateStatus(freshRes);
                    bunches.put(resFullName, updatedRes);
                    logger.info("Status of [{}] resource successfully updated", resFullName);
                    return updatedRes;
                } else {
                    logger.warn("Unable to update status of [{}] resource: resource not present", resFullName);
                    return resource;
                }
            }

            throw e;
        }
    }

    protected void setupAndCreateKubObj(CR resource) {

        var kubObj = loadKubObj(getKubObjDefPath(resource));

        setupKubObj(resource, kubObj);

        createKubObj(extractNamespace(resource), kubObj);

        log("Based on [{}<{}.{}>] resource, kubernetes object [{}<{}.{}>] has been created", resource, kubObj);

        resource.getStatus().succeeded(kubObjType + " successfully deployed", extractName(kubObj));

        updateStatus(resource);
    }

    protected void setupKubObj(CR resource, KO kubObj) {

        mapProperties(resource, kubObj);

        log("Additional properties from [{}<{}.{}>] has been set to [{}<{}.{}>]", resource, kubObj);


        kubObj.getMetadata().setOwnerReferences(List.of(createOwnerReference(resource)));

        log("Property 'OwnerReference' with reference to [{}<{}.{}>] has been set to [{}<{}.{}>]", resource, kubObj);

    }

    protected void startWatchForKubObj(CR resource) {
        targetCrFullNames.add(extractFullName(resource));
        if (Objects.isNull(kubObjWatch)) {
            kubObjWatch = setKubObjWatcher(extractNamespace(resource), new KubObjWatcher());
        }
    }

    protected void mapProperties(CR resource, KO kubObj) {

        var kubObjMD = kubObj.getMetadata();

        var resMD = resource.getMetadata();

        var resName = resMD.getName();

        var resNamespace = extractNamespace(resource);


        kubObjMD.setName(resName);

        kubObjMD.setNamespace(extractNamespace(resource));

        kubObjMD.setLabels(resMD.getLabels());

        kubObjMD.setAnnotations(resMD.getAnnotations());

        kubObjMD.setAnnotations(kubObjMD.getAnnotations() != null ? kubObjMD.getAnnotations() : new HashMap<>());

        kubObjMD.getAnnotations().put(ANTECEDENT_LABEL_KEY_ALIAS, resNamespace + ":" + resourceType + "/" + resName);

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

    protected boolean isResourceExist(String resourceFullName) {
        return Objects.nonNull(bunches.get(resourceFullName));
    }

    protected void log(String msg, CR resource, KO kubObj) {
        logger.info(
                msg,
                resourceType, extractNamespace(resource), extractName(resource),
                kubObjType, extractNamespace(kubObj), extractName(kubObj)
        );
    }

    protected void debug(String format, Object... arguments) {
        if (logger.isDebugEnabled()) {
            logger.debug(format, arguments);
        }
    }


    protected abstract String getKubObjDefPath(CR resource);

    protected abstract void createKubObj(String namespace, KO kubObj);

    protected abstract Watch setKubObjWatcher(String namespace, Watcher<KO> objWatcher);


    protected class KubObjWatcher implements Watcher<KO> {

        @Override
        public void eventReceived(Action action, KO kubObj) {
            var currentOwnerFullName = extractOwnerFullName(kubObj);

            if (Objects.isNull(currentOwnerFullName) || !targetCrFullNames.contains(currentOwnerFullName)) {
                return;
            }

            debug("Received event '{}' for {}", action, kubObj);

            var kubObjFullName = extractFullName(kubObj);

            if (action.equals(DELETED)) {
                logger.info("[{}<{}>] has been deleted. Trying to restart ...", kubObjType, kubObjFullName);

                if (isResourceExist(currentOwnerFullName)) {

                    var kubObjMD = kubObj.getMetadata();

                    kubObjMD.setUid(null);

                    kubObjMD.setResourceVersion(null);

                    createKubObj(extractNamespace(kubObj), kubObj);

                    logger.info("[{}<{}>] has been restarted", kubObjType, kubObjFullName);

                } else {
                    logger.warn("Owner of [{}<{}>] not present", kubObjType, kubObjFullName);
                }

            }

        }

        @Override
        public void onClose(KubernetesClientException cause) {
            if (cause != null) {
                logger.error("Watcher has been closed cause: {}", cause.getMessage(), cause);
            }

            if (kubObjWatch != null) {
                kubObjWatch.close();
            }
        }

    }

}
