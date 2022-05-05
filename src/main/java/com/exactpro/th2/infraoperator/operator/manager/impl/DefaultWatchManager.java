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

import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op;
import com.exactpro.th2.infraoperator.operator.context.HelmOperatorContext;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.Strings;
import com.fasterxml.uuid.Generators;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.operator.AbstractTh2Operator.REFRESH_TOKEN_ALIAS;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;

public class DefaultWatchManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultWatchManager.class);

    private boolean isWatching = false;

    private final Builder operatorBuilder;

    private final List<ResourceClient<Th2CustomResource>> resourceClients = new ArrayList<>();

    private final List<Supplier<HelmReleaseTh2Op<Th2CustomResource>>> helmWatchersCommands = new ArrayList<>();

    private final SharedInformerFactory sharedInformerFactory;

    private static DefaultWatchManager instance;

    private final EventDispatcher eventDispatcher;

    private synchronized SharedInformerFactory getInformerFactory() {
        return sharedInformerFactory;
    }

    private DefaultWatchManager(Builder builder) {
        this.operatorBuilder = builder;
        this.sharedInformerFactory = builder.getClient().informers();
        this.eventDispatcher = new EventDispatcher();

        sharedInformerFactory.addSharedInformerEventListener(exception -> {
            logger.error("Exception in InformerFactory : {}", exception.getMessage());
        });
        instance = this;
    }

    public void startInformers() {
        logger.info("Starting all informers...");

        SharedInformerFactory sharedInformerFactory = getInformerFactory();

        eventDispatcher.start();
        EventHandlerContext eventHandlerContext = registerInformers(sharedInformerFactory);
        loadResources(eventHandlerContext);

        isWatching = true;

        sharedInformerFactory.startAllRegisteredInformers();
        logger.info("All informers has been started");
    }

    public void stopInformers() {
        logger.info("Shutting down informers");
        getInformerFactory().stopAllRegisteredInformers();
    }

    private void loadResources(EventHandlerContext context) {
        loadConfigMaps(context);
        loadLinks(context);
    }

    private void loadLinks(EventHandlerContext context) {

        var th2LinkEventHandler = (Th2LinkEventHandler) context.getHandler(Th2LinkEventHandler.class);
        var linkClient = th2LinkEventHandler.getLinkClient();
        List<Th2Link> th2Links = linkClient.getInstance().inAnyNamespace().list().getItems();
        th2Links = filterByNamespace(th2Links);
        for (var th2Link : th2Links) {
            logger.info("Loading \"{}\"", annotationFor(th2Link));
            th2LinkEventHandler.eventReceived(Watcher.Action.ADDED, th2Link);
        }
    }

    private void loadConfigMaps(EventHandlerContext context) {

        var configMapEventHandler = (ConfigMapEventHandler) context.getHandler(ConfigMapEventHandler.class);
        KubernetesClient client = operatorBuilder.getClient();
        List<ConfigMap> configMaps = client.configMaps().inAnyNamespace().list().getItems();
        configMaps = filterByNamespace(configMaps);
        for (var configMap : configMaps) {
            logger.info("Loading \"{}\"", annotationFor(configMap));
            configMapEventHandler.eventReceived(Watcher.Action.ADDED, configMap);
        }
    }

    private <E extends HasMetadata> List<E> filterByNamespace(List<E> resources) {
        return resources.stream()
                .filter(resource -> !Strings.nonePrefixMatch(resource.getMetadata().getNamespace(),
                        OperatorConfig.INSTANCE.getNamespacePrefixes()))
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
        KubernetesClient client = operatorBuilder.getClient();

        context.addHandler(NamespaceEventHandler.newInstance(sharedInformerFactory, eventDispatcher.getEventQueue()));
        context.addHandler(Th2LinkEventHandler.newInstance(sharedInformerFactory, client,
                eventDispatcher.getEventQueue()));
        context.addHandler(Th2DictionaryEventHandler.newInstance(sharedInformerFactory, client,
                eventDispatcher.getEventQueue()));
        context.addHandler(ConfigMapEventHandler.newInstance(sharedInformerFactory, client,
                eventDispatcher.getEventQueue()));

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

            var handler = new GenericResourceEventHandler<>(
                    helmReleaseTh2Op,
                    eventDispatcher.getEventQueue());
            helmReleaseTh2Op.generateInformerFromFactory(getInformerFactory()).addEventHandler(handler);
            context.addHandler(handler);
        }

        // needs to be converted to Watcher
        CRDEventHandler.newInstance(sharedInformerFactory);
        return context;
    }

    public boolean isWatching() {
        return isWatching;
    }

    public <T extends Th2CustomResource> void addTarget(
            Function<HelmOperatorContext.Builder<?, ?>, HelmReleaseTh2Op<T>> operator) {

        helmWatchersCommands.add(() -> {
            // T extends Th2CustomResource -> T is a Th2CustomResource
            @SuppressWarnings("unchecked")
            var th2ResOp = (HelmReleaseTh2Op<Th2CustomResource>) operator.apply(operatorBuilder);

            return th2ResOp;
        });
    }

    void refreshBoxes(String namespace) {
         refreshBoxes(namespace, null, true);
    }

    void refreshBoxes(String namespace, Set<String> boxes) {
        refreshBoxes(namespace, boxes, false);
    }

    private void refreshBoxes(String namespace, Set<String> boxes, boolean refreshAllBoxes) {

        if (!refreshAllBoxes && (boxes == null || boxes.size() == 0)) {
            logger.warn("Empty set of boxes was given to refresh");
            return;
        }

        if (!isWatching()) {
            logger.warn("Not watching for resources yet");
            return;
        }

        var refreshedBoxes = 0;
        for (var resourceClient : resourceClients) {
            var mixedOperation = resourceClient.getInstance();
            for (var res : mixedOperation.inNamespace(namespace).list().getItems()) {
                if (refreshAllBoxes || boxes.contains(extractName(res))) {
                    createResource(namespace, res, resourceClient);
                    refreshedBoxes++;
                }
            }
        }

        logger.info("{} boxes updated", refreshedBoxes);
    }

    private void createResource(String linkNamespace, Th2CustomResource resource,
                                ResourceClient<Th2CustomResource> resClient) {
        var refreshToken = Generators.timeBasedGenerator().generate().toString();
        var resMeta = resource.getMetadata();
        resMeta.setResourceVersion(null);
        resMeta.setAnnotations(Objects.nonNull(resMeta.getAnnotations()) ? resMeta.getAnnotations() : new HashMap<>());
        resMeta.getAnnotations().put(REFRESH_TOKEN_ALIAS, refreshToken);
        resClient.getInstance().inNamespace(linkNamespace).createOrReplace(resource);
        logger.debug("refreshed \"{}\" with refresh-token={}",
                CustomResourceUtils.annotationFor(resource), refreshToken);
    }

    public static Builder builder(KubernetesClient client) {
        return new Builder(client);
    }

    public static synchronized DefaultWatchManager getInstance() {
        if (instance == null) {
            instance = DefaultWatchManager.builder(new DefaultKubernetesClient()).build();
        }

        return instance;
    }

    private static class Builder extends HelmOperatorContext.Builder<DefaultWatchManager, Builder> {

        public Builder(KubernetesClient client) {
            super(client);
        }

        @Override
        public DefaultWatchManager build() {
            return new DefaultWatchManager(this);
        }
    }

    public void close() {
        eventDispatcher.interrupt();
        operatorBuilder.getClient().close();
    }
}
