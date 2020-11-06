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

package com.exactpro.th2.infraoperator.fabric8.operator.manager.impl;

import com.exactpro.th2.infraoperator.fabric8.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.dictionary.factory.impl.DefaultDictionaryFactory;
import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.dictionary.factory.impl.EmptyDictionaryFactory;
import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc.factory.impl.DefaultGrpcRouterConfigFactory;
import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc.factory.impl.EmptyGrpcRouterConfigFactory;
import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.ipml.DictionaryClient;
import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.ipml.LinkClient;
import com.exactpro.th2.infraoperator.fabric8.operator.HelmReleaseTh2Op;
import com.exactpro.th2.infraoperator.fabric8.operator.context.HelmOperatorContext;
import com.exactpro.th2.infraoperator.fabric8.operator.manager.HelmWatchManager;
import com.exactpro.th2.infraoperator.fabric8.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.fabric8.spec.dictionary.Th2Dictionary;
import com.exactpro.th2.infraoperator.fabric8.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.fabric8.spec.link.singleton.LinkSingleton;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.Nameable;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.dictionary.impl.DefaultDictionaryLinkResolver;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.dictionary.impl.EmptyDictionaryLinkResolver;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.grpc.impl.DefaultGrpcLinkResolver;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.grpc.impl.EmptyGrpcLinkResolver;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.mq.impl.BindQueueLinkResolver;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.mq.impl.EmptyQueueLinkResolver;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.resFinder.box.EmptyBoxResourceFinder;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.resFinder.box.impl.DefaultBoxResourceFinder;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.resFinder.box.impl.StoreDependentBoxResourceFinder;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.resFinder.dictionary.DictionaryResourceFinder;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.resFinder.dictionary.impl.DefaultDictionaryResourceFinder;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.resFinder.dictionary.impl.EmptyDictionaryResourceFinder;
import com.fasterxml.uuid.Generators;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import lombok.Getter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.fabric8.configuration.OperatorConfig.MQ_CONFIG_MAP_NAME;
import static com.exactpro.th2.infraoperator.fabric8.configuration.OperatorConfig.MqWorkSpaceConfig.CONFIG_MAP_RABBITMQ_PROP_NAME;
import static com.exactpro.th2.infraoperator.fabric8.operator.AbstractTh2Operator.REFRESH_TOKEN_ALIAS;
import static com.exactpro.th2.infraoperator.fabric8.util.CustomResourceUtils.watchFor;
import static com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils.extractNamespace;
import static com.exactpro.th2.infraoperator.fabric8.util.JsonUtils.JSON_READER;
import static io.sundr.codegen.utils.StringUtils.isNullOrEmpty;


public class DefaultWatchManager implements HelmWatchManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultWatchManager.class);


    private boolean isWatching = false;

    private final Builder operatorBuilder;

    private final LinkClient linkClient;

    private final DictionaryClient dictionaryClient;

    private final List<Watch> watches = new ArrayList<>();

    private final List<ResourceClient<Th2CustomResource>> resourceClients = new ArrayList<>();

    private final List<HelmReleaseTh2Op<Th2CustomResource>> helmWatchers = new ArrayList<>();

    private final List<Supplier<HelmReleaseTh2Op<Th2CustomResource>>> helmWatchersCommands = new ArrayList<>();


    private DefaultWatchManager(Builder builder) {
        this.operatorBuilder = builder;
        this.linkClient = new LinkClient(operatorBuilder.getClient());
        this.dictionaryClient = new DictionaryClient(operatorBuilder.getClient());
    }


    @Override
    public void startWatching() {
        logger.info("Starting watching all resources...");

        postInit();

        start();

        isWatching = true;

        logger.info("All resources are watched");
    }

    @Override
    public void resetWatching() {
        logger.info("Restarting all resource watchers...");

        stopWatching();
        startWatching();

        logger.info("All resource watchers have been restarted");
    }

    @Override
    public void stopWatching() {
        logger.info("Stopping all resource watchers...");

        watches.forEach(Watch::close);

        isWatching = false;

        logger.info("All resource watchers have been stopped");
    }

    @Override
    public boolean isWatching() {
        return isWatching;
    }

    @Override
    public <T extends Th2CustomResource> void addTarget(Function<HelmOperatorContext.Builder<?, ?>, HelmReleaseTh2Op<T>> operator) {

        helmWatchersCommands.add(() -> {
            // T extends Th2CustomResource -> T is a Th2CustomResource
            @SuppressWarnings("unchecked")
            var th2ResOp = (HelmReleaseTh2Op<Th2CustomResource>) operator.apply(operatorBuilder);

            return th2ResOp;
        });

    }


    private int refreshBoxesIfNeeded(Th2Link oldLinkRes, Th2Link newLinkRes) {

        var linkNamespace = extractNamespace(oldLinkRes);

        if (Objects.isNull(linkNamespace)) {
            linkNamespace = extractNamespace(newLinkRes);
        }

        var boxesToUpdate = getBoxesToUpdate(oldLinkRes, newLinkRes);

        logger.info("{} box(es) need updating", boxesToUpdate.size());

        return refreshBoxes(linkNamespace, boxesToUpdate);
    }

    private Th2Link getOldLink(Th2Link th2Link, List<Th2Link> resourceLinks) {
        var oldLinkIndex = resourceLinks.indexOf(th2Link);
        return oldLinkIndex < 0 ? Th2Link.newInstance() : resourceLinks.get(oldLinkIndex);
    }

    private Set<String> getBoxesToUpdate(Th2Link oldLinkRes, Th2Link newLinkRes) {

        var oldBoxesLinks = oldLinkRes.getSpec().getBoxesRelation().getAllLinks();
        var newBoxesLinks = newLinkRes.getSpec().getBoxesRelation().getAllLinks();
        var fromBoxesLinks = getBoxesToUpdate(oldBoxesLinks, newBoxesLinks, blb -> Set.of(blb.getFrom().getBox(), blb.getTo().getBox()));
        Set<String> boxes = new HashSet<>(fromBoxesLinks);

        var oldLinks = oldLinkRes.getSpec().getDictionariesRelation();
        var newLinks = newLinkRes.getSpec().getDictionariesRelation();
        var fromDicLinks = getBoxesToUpdate(oldLinks, newLinks, dlb -> Set.of(dlb.getBox()));
        boxes.addAll(fromDicLinks);

        return boxes;
    }

    private <T extends Nameable> Set<String> getBoxesToUpdate(List<T> oldLinks, List<T> newLinks, Function<T, Set<String>> boxesExtractor) {

        Set<String> boxes = new HashSet<>();

        var or = new OrderedRelation<>(oldLinks, newLinks);

        for (var maxLink : or.getMaxLinks()) {
            var isLinkExist = false;
            for (var minLink : or.getMinLinks()) {
                if (minLink.getName().equals(maxLink.getName())) {
                    if (!minLink.equals(maxLink)) {
                        boxes.addAll(boxesExtractor.apply(minLink));
                        boxes.addAll(boxesExtractor.apply(maxLink));
                    }
                    isLinkExist = true;
                }
            }
            if (!isLinkExist) {
                boxes.addAll(boxesExtractor.apply(maxLink));
            }
        }

        var oldToUpdate = oldLinks.stream()
                .filter(t -> newLinks.stream().noneMatch(t1 -> t1.getName().equals(t.getName())))
                .flatMap(t -> boxesExtractor.apply(t).stream())
                .collect(Collectors.toSet());

        boxes.addAll(oldToUpdate);

        return boxes;
    }

    private int refreshBoxes(String linkNamespace) {
        return refreshBoxes(linkNamespace, null);
    }

    private int refreshBoxes(String linkNamespace, Set<String> boxes) {

        var refreshedBoxes = 0;

        if (!isWatching()) {
            logger.info("Resources not watching yet");
            return refreshedBoxes;
        }

        for (var rc : resourceClients) {
            var resClient = rc.getInstance();
            for (var res : resClient.inNamespace(linkNamespace).list().getItems()) {
                if (Objects.isNull(boxes) || boxes.contains(extractName(res))) {
                    createResource(linkNamespace, res, rc);
                    refreshedBoxes++;
                }
            }
        }

        return refreshedBoxes;
    }

    private void createResource(String linkNamespace, Th2CustomResource resource, ResourceClient<Th2CustomResource> resClient) {
        var refreshToken = Generators.timeBasedGenerator().generate().toString();
        var resMeta = resource.getMetadata();
        resMeta.setResourceVersion(null);
        resMeta.setAnnotations(Objects.nonNull(resMeta.getAnnotations()) ? resMeta.getAnnotations() : new HashMap<>());
        resMeta.getAnnotations().put(REFRESH_TOKEN_ALIAS, refreshToken);
        resClient.getInstance().inNamespace(linkNamespace).createOrReplace(resource);
    }


    private void start() {

        watches.add(watchFor(linkClient, new LinkWatcher()));

        watches.add(watchFor(dictionaryClient, new DictionaryWatcher()));

        watches.add(operatorBuilder.getClient().configMaps().inAnyNamespace().watch(new ConfigMapWatcher()));
        logger.info("Started watching for config map [ConfigMap<{}>]", MQ_CONFIG_MAP_NAME);

        helmWatchersCommands.forEach(hwSup -> {
            var hw = hwSup.get();
            resourceClients.add(hw.getResourceClient());
            helmWatchers.add(hw);
        });

        helmWatchers.forEach(hw -> watches.add(watchFor(hw.getResourceClient(), hw)));

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


    private static class OrderedRelation<T> {

        @Getter
        private List<T> maxLinks;

        @Getter
        private List<T> minLinks;

        private List<T> newLinks;

        private List<T> oldLinks;


        public OrderedRelation(List<T> oldLinks, List<T> newLinks) {
            this.oldLinks = oldLinks;
            this.newLinks = newLinks;
            if (newLinks.size() >= oldLinks.size()) {
                this.maxLinks = newLinks;
                this.minLinks = oldLinks;
            } else {
                this.maxLinks = oldLinks;
                this.minLinks = newLinks;
            }
        }


        public boolean minIsNew() {
            return minLinks == newLinks;
        }

        public boolean maxIsNew() {
            return maxLinks == newLinks;
        }
    }

    /**
     * Designed only to watch one config map - {@link OperatorConfig#MQ_CONFIG_MAP_NAME}
     */
    private class ConfigMapWatcher implements Watcher<ConfigMap> {

        @SneakyThrows
        @Override
        public void eventReceived(Action action, ConfigMap cm) {

            if (!Objects.equals(extractName(cm), MQ_CONFIG_MAP_NAME)) {
                return;
            }

            var namespace = extractNamespace(cm);

            synchronized (LinkSingleton.INSTANCE.getLock(namespace)) {

                logger.info("Received event '{}' for [ConfigMap<{}.{}>]", action, namespace, extractName(cm));

                var opConfig = OperatorConfig.INSTANCE;

                var mqWsConfig = opConfig.getMqWorkSpaceConfig(namespace);

                var configContent = cm.getData().get(CONFIG_MAP_RABBITMQ_PROP_NAME);

                if (isNullOrEmpty(configContent)) {
                    logger.warn("'{}' not found in '{}.{}' config map",
                            CONFIG_MAP_RABBITMQ_PROP_NAME, namespace, MQ_CONFIG_MAP_NAME);
                    return;
                }

                configContent = configContent.replace(System.lineSeparator(), "");

                var newMqWsConfig = JSON_READER.readValue(configContent, OperatorConfig.MqWorkSpaceConfig.class);

                if (!Objects.equals(mqWsConfig, newMqWsConfig)) {

                    opConfig.setMqWorkSpaceConfig(namespace, newMqWsConfig);

                    logger.info("RabbitMQ workspace data in namespace '{}' has been updated with '{}'. " +
                            "Updating all boxes in namespace '{}'", namespace, newMqWsConfig, namespace);

                    var refreshedBoxesCount = refreshBoxes(namespace);

                    logger.info("{} box-definition(s) updated", refreshedBoxesCount);

                } else {
                    logger.info("RabbitMQ workspace data hasn't changed");
                }

            }

        }

        @Override
        public void onClose(KubernetesClientException cause) {
            if (cause != null) {
                logger.error("Watcher has been closed cause: {}", cause.getMessage(), cause);
            }
        }

    }

    private class DictionaryWatcher implements Watcher<Th2Dictionary> {

        @Override
        public void eventReceived(Action action, Th2Dictionary dictionary) {

            logger.info("Received event '{}' for [Th2Dictionary<{}.{}>]", action, extractNamespace(dictionary), extractName(dictionary));


            logger.info("Updating all boxes that contains dictionary [Th2Dictionary<{}.{}>] ...", extractNamespace(dictionary), extractName(dictionary));

            var linkedResources = getLinkedResources(dictionary);

            logger.info("{} box(es) need updating", linkedResources.size());

            var refreshedBoxCount = refreshBoxes(extractNamespace(dictionary), linkedResources);

            logger.info("{} box-definition(s) updated", refreshedBoxCount);

        }

        @Override
        public void onClose(KubernetesClientException cause) {
            if (cause != null) {
                logger.error("Watcher has been closed cause: {}", cause.getMessage(), cause);
            }
        }


        private Set<String> getLinkedResources(Th2Dictionary dictionary) {
            Set<String> resources = new HashSet<>();

            var lSingleton = LinkSingleton.INSTANCE;

            for (var linkRes : lSingleton.getLinkResources(extractNamespace(dictionary))) {
                for (var dicLink : linkRes.getSpec().getDictionariesRelation()) {
                    if (dicLink.getDictionary().getName().contains(extractName(dictionary))) {
                        resources.add(dicLink.getBox());
                    }
                }
            }

            return resources;
        }

    }

    private class LinkWatcher implements Watcher<Th2Link> {

        @Override
        public void eventReceived(Action action, Th2Link th2Link) {

            var linkNamespace = extractNamespace(th2Link);

            logger.info("Received event '{}' for [Th2Link<{}.{}>]", action, linkNamespace, extractName(th2Link));

            synchronized (LinkSingleton.INSTANCE.getLock(linkNamespace)) {

                var linkSingleton = LinkSingleton.INSTANCE;

                var resourceLinks = new ArrayList<>(linkSingleton.getLinkResources(linkNamespace));

                var oldLinkRes = getOldLink(th2Link, resourceLinks);

                int refreshedBoxCount = 0;

                switch (action) {
                    case ADDED:
                        logger.info("Updating all dependent boxes according to provided links ...");

                        refreshedBoxCount = refreshBoxesIfNeeded(oldLinkRes, th2Link);

                        resourceLinks.remove(th2Link);

                        resourceLinks.add(th2Link);

                        break;
                    case MODIFIED:
                        logger.info("Updating all dependent boxes according to updated links ...");

                        refreshedBoxCount = refreshBoxesIfNeeded(oldLinkRes, th2Link);

                        resourceLinks.remove(th2Link);

                        resourceLinks.add(th2Link);

                        break;
                    case DELETED:
                    case ERROR:
                        logger.info("Updating all dependent boxes of destroyed links ...");

                        refreshedBoxCount = refreshBoxesIfNeeded(oldLinkRes, Th2Link.newInstance());

                        resourceLinks.remove(th2Link);

                        break;
                }

                logger.info("{} box-definition(s) updated", refreshedBoxCount);

                linkSingleton.setLinkResources(linkNamespace, resourceLinks);

            }

        }

        @Override
        public void onClose(KubernetesClientException cause) {
            if (cause != null) {
                logger.error("Watcher has been closed cause: {}", cause.getMessage(), cause);
            }
        }

    }


    public static Builder builder(KubernetesClient client) {
        return new Builder(client);
    }

    @Getter
    public static class Builder extends HelmOperatorContext.Builder<DefaultWatchManager, Builder> {

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

}
