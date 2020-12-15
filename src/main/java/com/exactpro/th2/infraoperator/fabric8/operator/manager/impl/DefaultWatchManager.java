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
import com.exactpro.th2.infraoperator.fabric8.configuration.RabbitMQConfig;
import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.dictionary.factory.impl.DefaultDictionaryFactory;
import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.dictionary.factory.impl.EmptyDictionaryFactory;
import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc.factory.impl.DefaultGrpcRouterConfigFactory;
import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc.factory.impl.EmptyGrpcRouterConfigFactory;
import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.ipml.DictionaryClient;
import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.ipml.LinkClient;
import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.configmaps.ConfigMaps;
import com.exactpro.th2.infraoperator.fabric8.operator.HelmReleaseTh2Op;
import com.exactpro.th2.infraoperator.fabric8.operator.context.HelmOperatorContext;
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
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.mq.impl.RabbitMQContext;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.resFinder.box.EmptyBoxResourceFinder;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.resFinder.box.impl.DefaultBoxResourceFinder;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.resFinder.box.impl.StoreDependentBoxResourceFinder;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.resFinder.dictionary.DictionaryResourceFinder;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.resFinder.dictionary.impl.DefaultDictionaryResourceFinder;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.resFinder.dictionary.impl.EmptyDictionaryResourceFinder;
import com.exactpro.th2.infraoperator.fabric8.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.fabric8.util.Strings;
import com.fasterxml.uuid.Generators;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.fabric8.configuration.RabbitMQConfig.CONFIG_MAP_RABBITMQ_PROP_NAME;
import static com.exactpro.th2.infraoperator.fabric8.operator.AbstractTh2Operator.REFRESH_TOKEN_ALIAS;
import static com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils.extractNamespace;
import static com.exactpro.th2.infraoperator.fabric8.util.JsonUtils.JSON_READER;

public class DefaultWatchManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultWatchManager.class);
    public static final String SECRET_TYPE_OPAQUE = "Opaque";

    private boolean isWatching = false;

    private final Builder operatorBuilder;

    private final LinkClient linkClient;

    private final DictionaryClient dictionaryClient;

    private final Set<Watch> watches = new HashSet<>();

    private final List<ResourceClient<Th2CustomResource>> resourceClients = new ArrayList<>();

    private final List<Supplier<HelmReleaseTh2Op<Th2CustomResource>>> helmWatchersCommands = new ArrayList<>();

    private DefaultWatchManager(Builder builder) {
        this.operatorBuilder = builder;
        this.linkClient = new LinkClient(operatorBuilder.getClient());
        this.dictionaryClient = new DictionaryClient(operatorBuilder.getClient());
    }

    public void startWatching() {
        logger.info("Starting watching all resources...");

        postInit();

        start();

        isWatching = true;

        logger.info("All resources are watched");
    }

    public void stopWatching() {
        logger.info("Stopping all resource watchers...");

        watches.forEach(Watch::close);
        isWatching = false;

        logger.info("All resource watchers have been stopped");
    }

    private void addWatch(Watch watch) {
        watches.add(watch);
    }

    private void removeWatch(Watch watch) {
        if (watches.contains(watch))
            watches.remove(watch);
        else
            throw new IllegalArgumentException("Watch to update was not found in the set");
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
        var fromBoxesLinks = getBoxesToUpdate(oldBoxesLinks, newBoxesLinks,
            blb -> Set.of(blb.getFrom().getBox(), blb.getTo().getBox()));
        Set<String> boxes = new HashSet<>(fromBoxesLinks);

        var oldLinks = oldLinkRes.getSpec().getDictionariesRelation();
        var newLinks = newLinkRes.getSpec().getDictionariesRelation();
        var fromDicLinks = getBoxesToUpdate(oldLinks, newLinks, dlb -> Set.of(dlb.getBox()));
        boxes.addAll(fromDicLinks);

        return boxes;
    }

    private <T extends Nameable> Set<String> getBoxesToUpdate(List<T> oldLinks, List<T> newLinks,
                                                              Function<T, Set<String>> boxesExtractor) {

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

    private void createResource(String linkNamespace, Th2CustomResource resource,
                                ResourceClient<Th2CustomResource> resClient) {
        var refreshToken = Generators.timeBasedGenerator().generate().toString();
        var resMeta = resource.getMetadata();
        resMeta.setResourceVersion(null);
        resMeta.setAnnotations(Objects.nonNull(resMeta.getAnnotations()) ? resMeta.getAnnotations() : new HashMap<>());
        resMeta.getAnnotations().put(REFRESH_TOKEN_ALIAS, refreshToken);
        resClient.getInstance().inNamespace(linkNamespace).createOrReplace(resource);
    }

    private void start() {

        addWatch(CustomResourceUtils.watchFor(linkClient, new LinkWatcher()));
        addWatch(CustomResourceUtils.watchFor(dictionaryClient, new DictionaryWatcher()));

        new ConfigMapWatcher(operatorBuilder.getClient(), this).watch();
        logger.info("Started watching for ConfigMaps");

        /*
             resourceClients initialization should be done first
             for concurrency issues
         */
        for (var hwSup : helmWatchersCommands) {
            HelmReleaseTh2Op<Th2CustomResource> helmReleaseTh2Op = hwSup.get();
            resourceClients.add(helmReleaseTh2Op.getResourceClient());
        }

        /*
            Appropriate watchers will be initialized afterwards
         */
        for (var hwSup : helmWatchersCommands) {
            HelmReleaseTh2Op<Th2CustomResource> helmReleaseTh2Op = hwSup.get();
            addWatch(CustomResourceUtils.watchFor(helmReleaseTh2Op.getResourceClient(), helmReleaseTh2Op));
        }
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
    }

    /**
     * Designed only to watch one config map - {@link OperatorConfig#getRabbitMQConfigMapName()}
     */
    private class ConfigMapWatcher implements Watcher<ConfigMap> {

        protected KubernetesClient client;
        protected DefaultWatchManager manager;
        protected Watch watch;

        public ConfigMapWatcher(KubernetesClient client, DefaultWatchManager manager) {
            this.client = client;
            this.manager = manager;
        }

        protected void watch() {
            if (watch != null) {
                watch.close();
                manager.removeWatch(watch);
            }

            watch = client.configMaps().inAnyNamespace().watch(this);
            manager.addWatch(watch);
            logger.info("Watch created ({})", this.getClass().getSimpleName());
        }

        @Override
        public final void onClose(KubernetesClientException cause) {
            if (cause != null) {
                logger.error("Watcher closed ({})", this.getClass().getSimpleName(), cause);
                watch();
            }
        }

        @Override
        public void eventReceived(Action action, ConfigMap configMap) {

            String namespace = configMap.getMetadata().getNamespace();
            List<String> namespacePrefixes = OperatorConfig.INSTANCE.getNamespacePrefixes();
            if (namespace != null
                && namespacePrefixes != null
                && namespacePrefixes.size() > 0
                && namespacePrefixes.stream().noneMatch(namespace::startsWith)) {
                return;
            }

            String resourceLabel = CustomResourceUtils.annotationFor(configMap);
            logger.debug("Received {} event for \"{}\"", action, resourceLabel);

            if (action == Action.DELETED)
                return;

            String configMapName = configMap.getMetadata().getName();

            if (!(configMapName.equals(OperatorConfig.INSTANCE.getRabbitMQConfigMapName())))
                return;

            try {
                logger.info("Processing {} event for \"{}\"", action, resourceLabel);

                if (configMapName.equals(OperatorConfig.INSTANCE.getRabbitMQConfigMapName())) {
                    synchronized (LinkSingleton.INSTANCE.getLock(namespace)) {
                        OperatorConfig opConfig = OperatorConfig.INSTANCE;
                        ConfigMaps configMaps = ConfigMaps.INSTANCE;
                        RabbitMQConfig rabbitMQConfig = configMaps.getRabbitMQConfig4Namespace(namespace);

                        String configContent = configMap.getData().get(CONFIG_MAP_RABBITMQ_PROP_NAME);
                        if (Strings.isNullOrEmpty(configContent)) {
                            logger.error("Key \"{}\" not found in \"{}\"", CONFIG_MAP_RABBITMQ_PROP_NAME,
                                resourceLabel);
                            return;
                        }

                        RabbitMQConfig newRabbitMQConfig = JSON_READER.readValue(configContent, RabbitMQConfig.class);
                        newRabbitMQConfig.setPassword(readRabbitMQPasswordForSchema(client, namespace,
                            opConfig.getSchemaSecrets().getRabbitMQ()));

                        if (!Objects.equals(rabbitMQConfig, newRabbitMQConfig)) {
                            configMaps.setRabbitMQConfig4Namespace(namespace, newRabbitMQConfig);
                            RabbitMQContext.createVHostIfAbsent(
                                namespace, opConfig.getRabbitMQManagementConfig());
                            logger.info("RabbitMQ ConfigMap has been updated in namespace \"%s\". Updating all boxes",
                                namespace);
                            int refreshedBoxesCount = refreshBoxes(namespace);
                            logger.info("{} box-definition(s) have been updated", refreshedBoxesCount);
                        } else
                            logger.info("RabbitMQ ConfigMap data hasn't changed");
                    }
                }
            } catch (Exception e) {
                logger.error("Exception processing {} event for \"{}\"", action, resourceLabel, e);
            }
        }
    }

    private String readRabbitMQPasswordForSchema(KubernetesClient client, String namespace, String secretName) throws Exception {

        Secret secret = client.secrets().inNamespace(namespace).withName(secretName).get();
        if (secret == null)
            throw new Exception(String.format("Secret not found \"%s\"",
                CustomResourceUtils.annotationFor(namespace, "Secret", secretName)));
        if (secret.getData() == null)
            throw new Exception(String.format("Invalid secret \"%s\". No data",
                CustomResourceUtils.annotationFor(secret)));

        String password = secret.getData().get(OperatorConfig.RABBITMQ_SECRET_PASSWORD_KEY);
        if (password == null)
            throw new Exception(String.format("Invalid secret \"%s\". No password was found with key \"%s\""
                , CustomResourceUtils.annotationFor(secret)
                , OperatorConfig.RABBITMQ_SECRET_PASSWORD_KEY));

        if (secret.getType().equals(SECRET_TYPE_OPAQUE))
            password = new String(Base64.getDecoder().decode(password.getBytes()));
        return password;
    }

    private class DictionaryWatcher implements Watcher<Th2Dictionary> {

        @Override
        public void eventReceived(Action action, Th2Dictionary dictionary) {

            String resourceLabel = CustomResourceUtils.annotationFor(dictionary);
            logger.debug("Received {} event for \"{}\"", action, resourceLabel);
            logger.info("Updating all boxes that contains dictionary \"{}\"", resourceLabel);

            var linkedResources = getLinkedResources(dictionary);

            logger.info("{} box(es) need updating", linkedResources.size());

            var refreshedBoxCount = refreshBoxes(extractNamespace(dictionary), linkedResources);

            logger.info("{} box-definition(s) updated", refreshedBoxCount);
        }

        @Override
        public final void onClose(KubernetesClientException cause) {
            if (cause != null)
                logger.error("Watcher closed ({})", this.getClass().getSimpleName(), cause);
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
            logger.debug("Received {} event for \"{}\"", action, CustomResourceUtils.annotationFor(th2Link));

            var linkNamespace = extractNamespace(th2Link);
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
            if (cause != null)
                logger.error("Watcher closed ({})", this.getClass().getSimpleName(), cause);
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
