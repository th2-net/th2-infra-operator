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

package com.exactpro.th2.infraoperator.spec.strategy.redeploy.tasks;

import com.exactpro.th2.infraoperator.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.fasterxml.uuid.Generators;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

import static com.exactpro.th2.infraoperator.operator.AbstractTh2Operator.REFRESH_TOKEN_ALIAS;
import static com.exactpro.th2.infraoperator.util.KubernetesUtils.PHASE_ACTIVE;

public class TriggerRedeployTask implements Task {

    private static final Logger logger = LoggerFactory.getLogger(TriggerRedeployTask.class);

    private final ResourceClient<? extends CustomResource> resourceClient;

    private final KubernetesClient kubClient;

    private final String boxName;

    private final String namespace;

    private final long retryDelay;

    private final Watcher.Action action;

    Watcher watcher;

    public TriggerRedeployTask(
            Watcher watcher,
            ResourceClient<? extends CustomResource> resourceClient,
            KubernetesClient kubClient,
            CustomResource resource,
            Watcher.Action action,
            long retryDelay
    ) {

        ObjectMeta meta = resource.getMetadata();
        this.resourceClient = resourceClient;
        this.kubClient = kubClient;
        this.boxName = meta.getName();
        this.namespace = meta.getNamespace();
        this.retryDelay = retryDelay;
        this.watcher = watcher;
        this.action = action;

    }

    @Override
    public String getName() {
        return String.format("%s:%s:%s", TriggerRedeployTask.class.getName(), namespace, boxName);
    }

    @Override
    public long getRetryDelay() {
        return retryDelay;
    }

    @Override
    public void run() {
        redeploy();
    }

    private void redeploy() {

        if (!namespaceActive()) {
            return;
        }

        CustomResource resource = resourceClient.getInstance()
                .inNamespace(namespace)
                .withName(boxName)
                .get();

        String resourceLabel = CustomResourceUtils.annotationFor(resource);

        if (resource == null) {
            logger.warn("Cannot redeploy resource \"{}\" as it is deleted", resourceLabel);
            return;
        }

        switch (action) {
            case ADDED:
            case MODIFIED:
                refreshToken(resource);
                watcher.eventReceived(Watcher.Action.MODIFIED, resource);
                logger.info("Triggered redeploy for \"{}\"", resourceLabel);
                break;
            case DELETED:
                logger.info("action was DELETED, no need to redeploy \"{}\"", resourceLabel);
        }
    }

    private boolean namespaceActive() {

        Namespace n = kubClient.namespaces().withName(namespace).get();

        if (n == null || !n.getStatus().getPhase().equals(PHASE_ACTIVE)) {
            logger.warn("Cannot redeploy resource {} as namespace \"{}\" is in \"{}\" state."
                    , boxName, namespace, (n == null ? "Deleted" : n.getStatus().getPhase()));
            return false;
        }
        return true;
    }

    private void refreshToken(CustomResource resource) {
        String token = Generators.timeBasedGenerator().generate().toString();
        ObjectMeta resMeta = resource.getMetadata();
        resMeta.setAnnotations(resMeta.getAnnotations() == null ? new HashMap<>() : resMeta.getAnnotations());
        resMeta.getAnnotations().put(REFRESH_TOKEN_ALIAS, token);
    }
}
