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

package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.configuration.ConfigLoader;
import com.exactpro.th2.infraoperator.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.Strings;
import com.fasterxml.uuid.Generators;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.operator.AbstractTh2Operator.REFRESH_TOKEN_ALIAS;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.WatcherUtils.createExceptionHandler;

public class DefaultWatchManager implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWatchManager.class);

    private boolean isWatching = false;

    private final List<ResourceClient<Th2CustomResource>> resourceClients = new ArrayList<>();

    private final List<Supplier<HelmReleaseTh2Op<Th2CustomResource>>> helmWatchersCommands = new ArrayList<>();

    private final SharedInformerFactory sharedInformerFactory;

    private final EventDispatcher eventDispatcher;

    private final KubernetesClient kubClient;

    private final RabbitMQContext rabbitMQContext;

    private SharedInformerFactory getInformerFactory() {
        return sharedInformerFactory;
    }

    public DefaultWatchManager(KubernetesClient kubClient, RabbitMQContext rabbitMQContext) {
        this.sharedInformerFactory = kubClient.informers();
        this.eventDispatcher = new EventDispatcher();
        this.kubClient = kubClient;
        this.rabbitMQContext = rabbitMQContext;

        sharedInformerFactory.addSharedInformerEventListener(exception -> {
            LOGGER.error("Exception in InformerFactory : {}", exception.getMessage());
        });
    }

    public synchronized void startInformers() {
        LOGGER.info("Starting all informers...");

        SharedInformerFactory sharedInformerFactory = getInformerFactory();

        eventDispatcher.start();
        EventHandlerContext eventHandlerContext = registerInformers(sharedInformerFactory);
        loadResources(eventHandlerContext);

        isWatching = true;

        sharedInformerFactory.startAllRegisteredInformers();
        LOGGER.info("All informers has been started");
    }

    private void stopInformers() {
        LOGGER.info("Shutting down informers");
        getInformerFactory().stopAllRegisteredInformers();
    }

    private void loadResources(EventHandlerContext context) {
        loadConfigMaps(context);
    }

    private void loadConfigMaps(EventHandlerContext context) {

        var configMapEventHandler = (ConfigMapEventHandler) context.getHandler(ConfigMapEventHandler.class);
        List<ConfigMap> configMaps = kubClient.configMaps().inAnyNamespace().list().getItems();
        configMaps = filterByNamespace(configMaps);
        for (var configMap : configMaps) {
            LOGGER.info("Loading \"{}\"", annotationFor(configMap));
            configMapEventHandler.eventReceived(Watcher.Action.ADDED, configMap);
        }
    }

    private <E extends HasMetadata> List<E> filterByNamespace(List<E> resources) {
        return resources.stream()
                .filter(resource -> !Strings.nonePrefixMatch(resource.getMetadata().getNamespace(),
                        ConfigLoader.getConfig().getNamespacePrefixes()))
                .collect(Collectors.toList());
    }

    private class EventHandlerContext<T extends Watcher> {
        Map<Class, Watcher> eventHandlers = new ConcurrentHashMap<>();

        void addHandler(T eventHandler) {
            eventHandlers.put(eventHandler.getClass(), eventHandler);
        }

        Watcher getHandler(Class<T> clazz) {
            return eventHandlers.get(clazz);
        }
    }

    private EventHandlerContext registerInformers(SharedInformerFactory sharedInformerFactory) {

        EventHandlerContext context = new EventHandlerContext();

        context.addHandler(NamespaceEventHandler.newInstance(sharedInformerFactory,
                rabbitMQContext,
                eventDispatcher.getEventQueue()));
        context.addHandler(Th2DictionaryEventHandler.newInstance(sharedInformerFactory, kubClient,
                eventDispatcher.getEventQueue()));
        context.addHandler(ConfigMapEventHandler.newInstance(sharedInformerFactory, kubClient, rabbitMQContext,
                this, eventDispatcher.getEventQueue()));

        /*
             resourceClients initialization should be done first
             for concurrency issues
         */
        for (var hwSup : helmWatchersCommands) {
            HelmReleaseTh2Op<Th2CustomResource> helmReleaseTh2Op = hwSup.get();
            resourceClients.add(helmReleaseTh2Op.getResourceClient());
        }

        /*
            Appropriate informers will be registered afterwards
         */
        for (var hwSup : helmWatchersCommands) {
            HelmReleaseTh2Op<Th2CustomResource> helmReleaseTh2Op = hwSup.get();

            var handler = new BoxResourceEventHandler<>(
                    helmReleaseTh2Op,
                    eventDispatcher.getEventQueue());

            SharedIndexInformer<Th2CustomResource> customResourceInformer =
                    helmReleaseTh2Op.generateInformerFromFactory(getInformerFactory());
            customResourceInformer.exceptionHandler(createExceptionHandler(Th2CustomResource.class));
            customResourceInformer.addEventHandler(handler);
            context.addHandler(handler);
        }

        return context;
    }

    public synchronized boolean isWatching() {
        return isWatching;
    }

    public synchronized <T extends Th2CustomResource> void addTarget(
            BiFunction<KubernetesClient, RabbitMQContext, HelmReleaseTh2Op<T>> operator) {

        helmWatchersCommands.add(() -> {
            // T extends Th2CustomResource -> T is a Th2CustomResource
            @SuppressWarnings("unchecked")
            var th2ResOp = (HelmReleaseTh2Op<Th2CustomResource>) operator.apply(kubClient, rabbitMQContext);

            return th2ResOp;
        });
    }

    synchronized void refreshBoxes(String namespace) {
        if (!isWatching()) {
            LOGGER.warn("Not watching for resources yet");
            return;
        }

        var refreshedBoxes = 0;
        for (var resourceClient : resourceClients) {
            var mixedOperation = resourceClient.getInstance();
            for (var res : mixedOperation.inNamespace(namespace).list().getItems()) {
                createResource(namespace, res, resourceClient);
                refreshedBoxes++;
            }
        }

        LOGGER.info("{} boxes updated", refreshedBoxes);
    }

    private void createResource(String linkNamespace, Th2CustomResource resource,
                                ResourceClient<Th2CustomResource> resClient) {
        var refreshToken = Generators.timeBasedGenerator().generate().toString();
        var resMeta = resource.getMetadata();
        resMeta.setResourceVersion(null);
        resMeta.setAnnotations(Objects.nonNull(resMeta.getAnnotations()) ? resMeta.getAnnotations() : new HashMap<>());
        resMeta.getAnnotations().put(REFRESH_TOKEN_ALIAS, refreshToken);
        resClient.getInstance().inNamespace(linkNamespace).resource(resource).createOrReplace();
        LOGGER.debug("refreshed \"{}\" with refresh-token={}",
                CustomResourceUtils.annotationFor(resource), refreshToken);
    }

    @Override
    public synchronized void close() throws InterruptedException {
        stopInformers();
        eventDispatcher.interrupt();
        eventDispatcher.join(5_000);
        resourceClients.clear();
        helmWatchersCommands.clear();
    }
}
