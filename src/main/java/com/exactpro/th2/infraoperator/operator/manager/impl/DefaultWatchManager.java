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

import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.factory.impl.DefaultDictionaryFactory;
import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.factory.impl.EmptyDictionaryFactory;
import com.exactpro.th2.infraoperator.model.box.configuration.grpc.factory.impl.DefaultGrpcRouterConfigFactory;
import com.exactpro.th2.infraoperator.model.box.configuration.grpc.factory.impl.EmptyGrpcRouterConfigFactory;
import com.exactpro.th2.infraoperator.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.model.kubernetes.client.ipml.DictionaryClient;
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op;
import com.exactpro.th2.infraoperator.operator.context.HelmOperatorContext;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.dictionary.impl.DefaultDictionaryLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.dictionary.impl.EmptyDictionaryLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.grpc.impl.DefaultGrpcLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.grpc.impl.EmptyGrpcLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.mq.impl.BindQueueLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.mq.impl.EmptyQueueLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.box.EmptyBoxResourceFinder;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.box.impl.DefaultBoxResourceFinder;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.box.impl.StoreDependentBoxResourceFinder;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.dictionary.DictionaryResourceFinder;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.dictionary.impl.DefaultDictionaryResourceFinder;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.dictionary.impl.EmptyDictionaryResourceFinder;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.fasterxml.uuid.Generators;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.exactpro.th2.infraoperator.operator.AbstractTh2Operator.REFRESH_TOKEN_ALIAS;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;

public class DefaultWatchManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultWatchManager.class);

    private boolean isWatching = false;

    private final Builder operatorBuilder;

    private DictionaryClient dictionaryClient;

    private final List<ResourceClient<Th2CustomResource>> resourceClients = new ArrayList<>();

    private final List<Supplier<HelmReleaseTh2Op<Th2CustomResource>>> helmWatchersCommands = new ArrayList<>();

    private final SharedInformerFactory sharedInformerFactory;

    private static DefaultWatchManager instance;

    private synchronized SharedInformerFactory getInformerFactory() {
        return sharedInformerFactory;
    }

    private DefaultWatchManager(Builder builder) {
        this.operatorBuilder = builder;
        this.sharedInformerFactory = builder.getClient().informers();
        this.dictionaryClient = new DictionaryClient(operatorBuilder.getClient());

        sharedInformerFactory.addSharedInformerEventListener(exception -> {
            logger.error("Exception in InformerFactory : {}", exception.getMessage());
        });

        instance = this;
    }

    public void startInformers () {
        logger.info("Starting all informers...");

        SharedInformerFactory sharedInformerFactory = getInformerFactory();

        postInit();
        registerInformers (sharedInformerFactory);

        isWatching = true;

        sharedInformerFactory.startAllRegisteredInformers();
        logger.info("All informers has been started");
    }

    public void stopInformers () {
        logger.info("Shutting down informers");
        getInformerFactory().stopAllRegisteredInformers();
    }


    private void registerInformers (SharedInformerFactory sharedInformerFactory) {

        KubernetesClient client = operatorBuilder.getClient();
        Th2LinkEventHandler.newInstance(sharedInformerFactory, client);
        Th2DictionaryEventHandler.newInstance(sharedInformerFactory, dictionaryClient);
        ConfigMapEventHandler.newInstance(sharedInformerFactory, client);
        NamespaceEventHandler.newInstance(sharedInformerFactory);
        CRDEventHandler.newInstance(sharedInformerFactory);
        //HelmReleaseEventHandler.newInstance(sharedInformerFactory, client);

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

            helmReleaseTh2Op.generateInformerFromFactory(getInformerFactory()).addEventHandlerWithResyncPeriod(
                    CustomResourceUtils.resourceEventHandlerFor(helmReleaseTh2Op.getResourceClient(),
                            helmReleaseTh2Op.generateResourceEventHandler()),
                    0);
        }

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

    int refreshBoxes(String linkNamespace) {
        return refreshBoxes(linkNamespace, null);
    }

    int refreshBoxes(String linkNamespace, Set<String> boxes) {

        if (boxes != null && boxes.size() == 0) {
            logger.warn("Empty set of boxes was given to refresh");
            return 0;
        }

        if (!isWatching()) {
            logger.warn("Not watching for resources yet");
            return 0;
        }

        var refreshedBoxes = 0;
        for (var rc : resourceClients) {
            var resClient = rc.getInstance();
            for (var res : resClient.inNamespace(linkNamespace).list().getItems()) {
                if (Objects.isNull(boxes) || boxes.contains(extractName(res))) {
                    createResource(linkNamespace, res, rc);
                    refreshedBoxes++;
                }
            }
        }

        logger.info("{} boxes updated", refreshedBoxes);
        return refreshedBoxes;
    }

    private void createResource(String linkNamespace, Th2CustomResource resource,
                                ResourceClient<Th2CustomResource> resClient) {
        var refreshToken = Generators.timeBasedGenerator().generate().toString();
        var resMeta = resource.getMetadata();
        resMeta.setResourceVersion(null);
        resMeta.setAnnotations(Objects.nonNull(resMeta.getAnnotations()) ? resMeta.getAnnotations() : new HashMap<>());
        resMeta.getAnnotations().put(REFRESH_TOKEN_ALIAS, refreshToken);
        resClient.getInstance().inNamespace(linkNamespace).createOrReplace(resource);
    }

    private void postInit() {

        var queueGenLinkResolver = operatorBuilder.getQueueGenLinkResolver();
        var resourceFinder = operatorBuilder.getResourceFinder();
        var dicResourceFinder = operatorBuilder.getDictionaryResourceFinder();
        var grpcLinkResolver = operatorBuilder.getGrpcLinkResolver();
        var dictionaryLinkResolver = operatorBuilder.getDictionaryLinkResolver();
        var grpcConfigFactory = operatorBuilder.getGrpcConfigFactory();
        var dictionaryFactory = operatorBuilder.getDictionaryFactory();

        if (resourceFinder instanceof EmptyBoxResourceFinder || resourceFinder instanceof DefaultBoxResourceFinder) {
            var resFinder = new DefaultBoxResourceFinder(resourceClients);
            var msgStResFinder = new StoreDependentBoxResourceFinder(resFinder);
            operatorBuilder.resourceFinder(msgStResFinder);
        }

        if (dicResourceFinder instanceof EmptyDictionaryResourceFinder || dicResourceFinder instanceof DefaultDictionaryResourceFinder) {
            operatorBuilder.dictionaryResourceFinder(new DefaultDictionaryResourceFinder(dictionaryClient));
        }

        if (grpcLinkResolver instanceof EmptyGrpcLinkResolver || grpcLinkResolver instanceof DefaultGrpcLinkResolver) {
            operatorBuilder.grpcLinkResolver(new DefaultGrpcLinkResolver(operatorBuilder.getResourceFinder()));
        }

        if (queueGenLinkResolver instanceof EmptyQueueLinkResolver || queueGenLinkResolver instanceof BindQueueLinkResolver) {
            operatorBuilder.queueGenLinkResolver(new BindQueueLinkResolver(operatorBuilder.getResourceFinder()));
        }

        if (dictionaryLinkResolver instanceof EmptyDictionaryLinkResolver || dictionaryLinkResolver instanceof DefaultDictionaryLinkResolver) {
            var boxResFinder = operatorBuilder.getResourceFinder();
            var dicResFinder = operatorBuilder.getDictionaryResourceFinder();
            operatorBuilder.dictionaryLinkResolver(new DefaultDictionaryLinkResolver(boxResFinder, dicResFinder));
        }

        if (grpcConfigFactory instanceof EmptyGrpcRouterConfigFactory || grpcConfigFactory instanceof DefaultGrpcRouterConfigFactory) {
            operatorBuilder.grpcConfigFactory(new DefaultGrpcRouterConfigFactory(operatorBuilder.getResourceFinder()));
        }

        if (dictionaryFactory instanceof EmptyDictionaryFactory || dictionaryFactory instanceof DefaultDictionaryFactory) {
            operatorBuilder.dictionaryFactory(new DefaultDictionaryFactory(operatorBuilder.getDictionaryResourceFinder()));
        }
    }

    public static Builder builder(KubernetesClient client) {
        return new Builder(client);
    }


    public static synchronized DefaultWatchManager getInstance () {
        if (instance == null) {
            instance = DefaultWatchManager.builder(new DefaultKubernetesClient()).build();
        }

        return instance;
    }

    @Getter
    private static class Builder extends HelmOperatorContext.Builder<DefaultWatchManager, Builder> {

        private DictionaryResourceFinder dictionaryResourceFinder = new EmptyDictionaryResourceFinder();

        public Builder(KubernetesClient client) {
            super(client);
        }

        public Builder dictionaryResourceFinder(DefaultDictionaryResourceFinder dictionaryResourceFinder) {
            this.dictionaryResourceFinder = dictionaryResourceFinder;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public DefaultWatchManager build() {
            return new DefaultWatchManager(this);
        }
    }

    public void close () {
        operatorBuilder.getClient().close();
    }
}
