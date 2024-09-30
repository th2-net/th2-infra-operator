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

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.configuration.ConfigLoader;
import com.exactpro.th2.infraoperator.metrics.OperatorMetrics;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.box.Th2Box;
import com.exactpro.th2.infraoperator.spec.corebox.Th2CoreBox;
import com.exactpro.th2.infraoperator.spec.estore.Th2Estore;
import com.exactpro.th2.infraoperator.spec.job.Th2Job;
import com.exactpro.th2.infraoperator.spec.mstore.Th2Mstore;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.ContinuousTaskWorker;
import com.exactpro.th2.infraoperator.util.Strings;
import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.stamp;
import static com.exactpro.th2.infraoperator.util.KubernetesUtils.createKubernetesClient;

public class CheckResourceCacheTask implements Task {
    private static final Logger logger = LoggerFactory.getLogger(ContinuousTaskWorker.class);

    private final long retryDelay;

    private final KubernetesClient client = createKubernetesClient();

    private final Set<String> nsPrefixes = ConfigLoader.getConfig().getNamespacePrefixes();

    private final List<MixedOperation> operations = List.of(
            client.resources(Th2Box.class),
            client.resources(Th2CoreBox.class),
            client.resources(Th2Estore.class),
            client.resources(Th2Mstore.class),
            client.resources(Th2Job.class)
    );

    public CheckResourceCacheTask(long retryDelay) {
        this.retryDelay = retryDelay;
    }

    @Override
    public String getName() {
        return CheckResourceCacheTask.class.getName();
    }

    @Override
    public long getRetryDelay() {
        return retryDelay;
    }

    @Override
    public void run() {
        logger.info("Executing task: \"{}\"", getName());
        client.namespaces()
                .list()
                .getItems()
                .stream()
                .filter(ns -> !Strings.nonePrefixMatch(ns.getMetadata().getName(), nsPrefixes))
                .forEach(namespace -> checkResources(namespace.getMetadata().getName()));
        logger.info("Task: \"{}\" completed successfully", getName());
    }

    private <T extends Th2CustomResource, L extends DefaultKubernetesResourceList<T>> void checkResources(
            String namespace
    ) {
        logger.info("Checking resources for namespace: \"{}\"", namespace);
        for (MixedOperation<T, L, Resource<T>> operation : operations) {
            operation.inNamespace(namespace)
                    .list()
                    .getItems()
                    .forEach(resource -> checkResource(resource, namespace));
        }
        logger.info("resources for namespace: \"{}\" are up to date", namespace);
    }

    private void checkResource(HasMetadata resource, String namespace) {
        String resourceName = resource.getMetadata().getName();
        var cachedResource = OperatorState.INSTANCE.getResourceFromCache(resourceName, namespace);
        if (!stamp(resource).equals(stamp(cachedResource))) {
            String error = "resource " + resourceName + "from kubernetes is different than cached version";
            logger.error(error);
            OperatorMetrics.incrementCacheErrors();
            throw new RuntimeException(error);
        }
    }
}
