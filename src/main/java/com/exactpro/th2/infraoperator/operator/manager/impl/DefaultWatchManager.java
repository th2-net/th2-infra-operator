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

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.configuration.RabbitMQConfig;
import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.factory.impl.DefaultDictionaryFactory;
import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.factory.impl.EmptyDictionaryFactory;
import com.exactpro.th2.infraoperator.model.box.configuration.grpc.factory.impl.DefaultGrpcRouterConfigFactory;
import com.exactpro.th2.infraoperator.model.box.configuration.grpc.factory.impl.EmptyGrpcRouterConfigFactory;
import com.exactpro.th2.infraoperator.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.model.kubernetes.client.ipml.DictionaryClient;
import com.exactpro.th2.infraoperator.model.kubernetes.client.ipml.LinkClient;
import com.exactpro.th2.infraoperator.model.kubernetes.configmaps.ConfigMaps;
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op;
import com.exactpro.th2.infraoperator.operator.context.HelmOperatorContext;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.dictionary.Th2Dictionary;
import com.exactpro.th2.infraoperator.spec.dictionary.Th2DictionaryList;
import com.exactpro.th2.infraoperator.spec.helmRelease.HelmRelease;
import com.exactpro.th2.infraoperator.spec.helmRelease.HelmReleaseList;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.Th2LinkList;
import com.exactpro.th2.infraoperator.spec.shared.Identifiable;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.dictionary.impl.DefaultDictionaryLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.dictionary.impl.EmptyDictionaryLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.grpc.impl.DefaultGrpcLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.grpc.impl.EmptyGrpcLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.mq.impl.BindQueueLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.mq.impl.EmptyQueueLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.mq.impl.RabbitMQContext;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.box.EmptyBoxResourceFinder;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.box.impl.DefaultBoxResourceFinder;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.box.impl.StoreDependentBoxResourceFinder;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.dictionary.DictionaryResourceFinder;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.dictionary.impl.DefaultDictionaryResourceFinder;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.dictionary.impl.EmptyDictionaryResourceFinder;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.Strings;
import com.fasterxml.uuid.Generators;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerEventListener;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.configuration.RabbitMQConfig.CONFIG_MAP_RABBITMQ_PROP_NAME;
import static com.exactpro.th2.infraoperator.operator.AbstractTh2Operator.REFRESH_TOKEN_ALIAS;
import static com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.HELM_RELEASE_CRD_NAME;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.annotationFor;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;
import static com.exactpro.th2.infraoperator.util.JsonUtils.JSON_READER;

public class DefaultWatchManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultWatchManager.class);
    public static final String SECRET_TYPE_OPAQUE = "Opaque";
    private static final String ANTECEDENT_LABEL_KEY_ALIAS = "th2.exactpro.com/antecedent";

    private boolean isWatching = false;

    private final Builder operatorBuilder;

    private final LinkClient linkClient;

    private final DictionaryClient dictionaryClient;

    private final Set<Watch> watches = new HashSet<>();

    private final List<ResourceClient<Th2CustomResource>> resourceClients = new ArrayList<>();

    private final List<Supplier<HelmReleaseTh2Op<Th2CustomResource>>> helmWatchersCommands = new ArrayList<>();

    private final SharedInformerFactory sharedInformerFactory;

    private synchronized SharedInformerFactory getInformerFactory() {
        return sharedInformerFactory;
    }

    private DefaultWatchManager(Builder builder) {
        this.operatorBuilder = builder;
        this.sharedInformerFactory = builder.getClient().informers();
        this.linkClient = new LinkClient(operatorBuilder.getClient());
        this.dictionaryClient = new DictionaryClient(operatorBuilder.getClient());

        sharedInformerFactory.addSharedInformerEventListener(new SharedInformerEventListener() {
            @Override
            public void onException(Exception exception) {
                logger.error("Exception in InformerFactory : {}", exception.getMessage());
            }
        });
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

    private void registerInformerForLinks (SharedInformerFactory sharedInformerFactory) {
        SharedIndexInformer<Th2Link> linkInformer = sharedInformerFactory.sharedIndexInformerForCustomResource(
                CustomResourceDefinitionContext.fromCrd(linkClient.getCustomResourceDefinition()),
                Th2Link.class,
                Th2LinkList.class,
                CustomResourceUtils.RESYNC_TIME);

        linkInformer.addEventHandlerWithResyncPeriod(CustomResourceUtils.resourceEventHandlerFor(
                new LinkResourceEventHandler(),
                Th2Link.class,
                linkClient.getCustomResourceDefinition()),
                0);
    }

    private void registerInformerForDictionaries (SharedInformerFactory sharedInformerFactory) {
        SharedIndexInformer<Th2Dictionary> dictionaryInformer = sharedInformerFactory.sharedIndexInformerForCustomResource(
                CustomResourceDefinitionContext.fromCrd(dictionaryClient.getCustomResourceDefinition()),
                Th2Dictionary.class,
                Th2DictionaryList.class,
                CustomResourceUtils.RESYNC_TIME);

        dictionaryInformer.addEventHandlerWithResyncPeriod(CustomResourceUtils.resourceEventHandlerFor(
                new DictionaryEventHandler(),
                Th2Dictionary.class,
                dictionaryClient.getCustomResourceDefinition()),
                0);
    }

    private void registerInformerForConfigMaps (SharedInformerFactory sharedInformerFactory) {
        SharedIndexInformer<ConfigMap> configMapInformer = sharedInformerFactory.sharedIndexInformerFor(
                ConfigMap.class,
                ConfigMapList.class,
                CustomResourceUtils.RESYNC_TIME);

        configMapInformer.addEventHandlerWithResyncPeriod(new ConfigMapEventHandler(operatorBuilder.getClient()), 0);
    }

    private void registerInformerForNamespaces (SharedInformerFactory sharedInformerFactory) {
        SharedIndexInformer<Namespace> namespaceInformer = sharedInformerFactory.sharedIndexInformerFor(
                Namespace.class,
                NamespaceList.class,
                CustomResourceUtils.RESYNC_TIME);

        namespaceInformer.addEventHandlerWithResyncPeriod(new NamespaceEventHandler(), 0);
    }

    private void registerInformerForCRDs (SharedInformerFactory sharedInformerFactory) {
        SharedIndexInformer<CustomResourceDefinition> crdInformer = sharedInformerFactory.sharedIndexInformerFor(
                CustomResourceDefinition.class,
                CustomResourceDefinitionList.class,
                CustomResourceUtils.RESYNC_TIME);

        List<String> crdNames = List.of(
                "th2boxes.th2.exactpro.com",
                "th2coreboxes.th2.exactpro.com",
                "th2dictionaries.th2.exactpro.com",
                "th2estores.th2.exactpro.com",
                "th2links.th2.exactpro.com",
                "th2mstores.th2.exactpro.com");

        crdInformer.addEventHandlerWithResyncPeriod(new CRDResourceEventHandler(crdNames), 0);
    }

    private void registerInformerForHelmReleases(SharedInformerFactory factory, KubernetesClient client) {
        var helmReleaseCrd = CustomResourceUtils.getResourceCrd(client, HELM_RELEASE_CRD_NAME);

        SharedIndexInformer<HelmRelease> helmReleaseInformer = factory.sharedIndexInformerForCustomResource(
                new CustomResourceDefinitionContext.Builder()
                        .withGroup(helmReleaseCrd.getSpec().getGroup())
                        .withVersion(helmReleaseCrd.getSpec().getVersions().get(0).getName())
                        .withScope(helmReleaseCrd.getSpec().getScope())
                        .withPlural(helmReleaseCrd.getSpec().getNames().getPlural())
                        .build(),
                HelmRelease.class,
                HelmReleaseList.class,
                CustomResourceUtils.RESYNC_TIME);

        helmReleaseInformer.addEventHandlerWithResyncPeriod(CustomResourceUtils.resourceEventHandlerFor(
                new HelmReleaseEventHandler(client),
                HelmRelease.class,
                dictionaryClient.getCustomResourceDefinition()),
                0);
    }

    private void registerInformers (SharedInformerFactory sharedInformerFactory) {
        registerInformerForLinks(sharedInformerFactory);
        registerInformerForDictionaries(sharedInformerFactory);
        registerInformerForConfigMaps(sharedInformerFactory);
        registerInformerForNamespaces(sharedInformerFactory);
        registerInformerForCRDs(sharedInformerFactory);
//        registerInformerForHelmReleases(sharedInformerFactory, operatorBuilder.getClient());

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

    private int refreshBoxesIfNeeded(Th2Link oldLinkRes, Th2Link newLinkRes) {

        var linkNamespace = extractNamespace(oldLinkRes);

        if (linkNamespace == null) {
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
                blb -> Set.of(blb.getFrom().getBoxName(), blb.getTo().getBoxName()));
        Set<String> boxes = new HashSet<>(fromBoxesLinks);

        var oldLinks = oldLinkRes.getSpec().getDictionariesRelation();
        var newLinks = newLinkRes.getSpec().getDictionariesRelation();
        var fromDicLinks = getBoxesToUpdate(oldLinks, newLinks, dlb -> Set.of(dlb.getBox()));
        boxes.addAll(fromDicLinks);

        return boxes;
    }

    private <T extends Identifiable> Set<String> getBoxesToUpdate(List<T> oldLinks, List<T> newLinks,
                                                                  Function<T, Set<String>> boxesExtractor) {

        Set<String> boxes = new HashSet<>();

        var or = new OrderedRelation<>(oldLinks, newLinks);

        for (var maxLink : or.getMaxLinks()) {
            var isLinkExist = false;
            for (var minLink : or.getMinLinks()) {
                if (minLink.getId().equals(maxLink.getId())) {
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
                .filter(t -> newLinks.stream().noneMatch(t1 -> t1.getId().equals(t.getId())))
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

        public OrderedRelation(List<T> oldLinks, List<T> newLinks) {
            if (newLinks.size() >= oldLinks.size()) {
                this.maxLinks = newLinks;
                this.minLinks = oldLinks;
            } else {
                this.maxLinks = oldLinks;
                this.minLinks = newLinks;
            }
        }
    }

    private class HelmReleaseEventHandler implements ResourceEventHandler<HelmRelease> {
        private final KubernetesClient client;
        private final MixedOperation<HelmRelease, HelmReleaseList, Resource<HelmRelease>> helmReleaseClient;

        public HelmReleaseEventHandler (KubernetesClient client) {
            this.client = client;

            var helmReleaseCrd = CustomResourceUtils.getResourceCrd(client, HELM_RELEASE_CRD_NAME);

            CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                    .withGroup(helmReleaseCrd.getSpec().getGroup())
                    .withVersion(helmReleaseCrd.getSpec().getVersions().get(0).getName())
                    .withScope(helmReleaseCrd.getSpec().getScope())
                    .withPlural(helmReleaseCrd.getSpec().getNames().getPlural())
                    .build();

            helmReleaseClient = client.customResources(
                    crdContext,
                    HelmRelease.class,
                    HelmReleaseList.class
            );
        }

        @Override
        public void onAdd(HelmRelease helmRelease) {

        }

        @Override
        public void onUpdate(HelmRelease oldHelmRelease, HelmRelease newHelmRelease) {

        }

        @Override
        public void onDelete(HelmRelease helmRelease, boolean deletedFinalStateUnknown) {
            String resourceLabel = annotationFor(helmRelease);
            if (!helmRelease.getMetadata().getAnnotations().containsKey(ANTECEDENT_LABEL_KEY_ALIAS)) {
                logger.info("\"{}\" doesn't have ANTECEDENT annotation, probably operator deleted it. it won't be redeployed!", resourceLabel);

                return;
            }

            logger.info("\"{}\" has been deleted. Trying to redeploy", resourceLabel);

            String namespace = helmRelease.getMetadata().getNamespace();
            Namespace namespaceObj = client.namespaces().withName(namespace).get();
            if (namespaceObj == null || !namespaceObj.getStatus().getPhase().equals("Active")) {
                logger.info("Namespace \"{}\" deleted or not active, cancelling", namespace);
                return;
            }

            ObjectMeta kubObjMD = helmRelease.getMetadata();
            kubObjMD.setUid(null);
            kubObjMD.setResourceVersion(null);
            helmReleaseClient.inNamespace(namespace).createOrReplace(helmRelease);

            logger.info("\"{}\" has been redeployed", resourceLabel);
        }
    }

    private class CRDResourceEventHandler implements ResourceEventHandler<CustomResourceDefinition> {
        public CRDResourceEventHandler (List<String> crdNames) {
            if (crdNames == null) {
                logger.error("Can't initialize CRDResourceEventHandler, crdNames is null");
                return;
            }

            this.crdNames = crdNames;
        }

        private List<String> crdNames;

        private boolean notInCrdNames (String crdName) {
            return !(crdNames.stream().anyMatch(el -> el.equals(crdName)));
        }

        @Override
        public void onAdd(CustomResourceDefinition crd) {
            if (notInCrdNames(crd.getMetadata().getName())) {
                return;
            }

            logger.debug("Received ADDED event for \"{}\"", CustomResourceUtils.annotationFor(crd));
        }

        @Override
        public void onUpdate(CustomResourceDefinition oldCrd, CustomResourceDefinition newCrd) {
            if (notInCrdNames(oldCrd.getMetadata().getName())
                || oldCrd.getMetadata().getResourceVersion().equals(newCrd.getMetadata().getResourceVersion()))
                return;

            logger.info("CRD old ResourceVersion {}, new ResourceVersion {}", oldCrd.getMetadata().getResourceVersion(), newCrd.getMetadata().getResourceVersion());
            logger.error("Modification detected for CustomResourceDefinition \"{}\". going to shutdown...", oldCrd.getMetadata().getName());
            System.exit(1);       
        }

        @Override
        public void onDelete(CustomResourceDefinition crd, boolean deletedFinalStateUnknown) {
            if (notInCrdNames(crd.getMetadata().getName()))
                return;

            logger.error("Modification detected for CustomResourceDefinition \"{}\". going to shutdown...", crd.getMetadata().getName());
            System.exit(1);
        }
    }

    private static class NamespaceEventHandler implements ResourceEventHandler<Namespace> {
        @Override
        public void onAdd(Namespace namespace) {
            if (Strings.nonePrefixMatch(namespace.getMetadata().getName(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
                return;
            }

            logger.debug("Received ADDED event for namespace: \"{}\"", namespace.getMetadata().getName());
        }

        @Override
        public void onUpdate(Namespace oldNamespace, Namespace newNamespace) {
            if (Strings.nonePrefixMatch(oldNamespace.getMetadata().getName(), OperatorConfig.INSTANCE.getNamespacePrefixes())
                && Strings.nonePrefixMatch(newNamespace.getMetadata().getName(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
                return;
            }

            logger.debug("Received MODIFIED event for namespace: \"{}\"", newNamespace.getMetadata().getName());
        }

        @Override
        public void onDelete(Namespace namespace, boolean deletedFinalStateUnknown) {
            String namespaceName = namespace.getMetadata().getName();

            if (Strings.nonePrefixMatch(namespace.getMetadata().getName(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
                return;
            }

            logger.debug("Received DELETED event for namespace: \"{}\"", namespaceName);

            var lock = OperatorState.INSTANCE.getLock(namespaceName);

            try {
                lock.lock();

                logger.debug("Processing event DELETED for namespace: \"{}\"", namespaceName);
                RabbitMQContext.cleanupVHost(namespaceName);
            } finally {
                lock.unlock();
            }
        }
    }

    private class ConfigMapEventHandler implements ResourceEventHandler<ConfigMap> {

        protected KubernetesClient client;

        public ConfigMapEventHandler (KubernetesClient client) {
            this.client = client;
        }

        private void processEvent (Watcher.Action action, ConfigMap configMap) {
            String resourceLabel = annotationFor(configMap);
            String namespace = configMap.getMetadata().getNamespace();
            String configMapName = configMap.getMetadata().getName();

            if (!(configMapName.equals(OperatorConfig.INSTANCE.getRabbitMQConfigMapName())))
                return;
            try {
                logger.info("Processing {} event for \"{}\"", action, resourceLabel);

                if (configMapName.equals(OperatorConfig.INSTANCE.getRabbitMQConfigMapName())) {
                    var lock = OperatorState.INSTANCE.getLock(namespace);
                    try {
                        lock.lock();

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
                            RabbitMQContext.createVHostIfAbsent(namespace);
                            logger.info("RabbitMQ ConfigMap has been updated in namespace \"{}\". Updating all boxes",
                                    namespace);
                            int refreshedBoxesCount = refreshBoxes(namespace);
                            logger.info("{} box-definition(s) have been updated", refreshedBoxesCount);
                        } else
                            logger.info("RabbitMQ ConfigMap data hasn't changed");
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (Exception e) {
                logger.error("Exception processing {} event for \"{}\"", action, resourceLabel, e);
            }
        }

        @Override
        public void onAdd(ConfigMap configMap) {
            long startDateTime = System.currentTimeMillis();

            if (Strings.nonePrefixMatch(configMap.getMetadata().getNamespace(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
                return;
            }

            String resourceLabel = annotationFor(configMap);
            logger.debug("Received ADD event for \"{}\"", annotationFor(configMap));

            processEvent(Watcher.Action.ADDED, configMap);

            long endDateTime = System.currentTimeMillis();
            logger.info("ADD Event for {} processed in {}ms",
                    resourceLabel,
                    (endDateTime - startDateTime));
        }

        @Override
        public void onUpdate(ConfigMap oldConfigMap, ConfigMap newConfigMap) {
            long startDateTime = System.currentTimeMillis();

            if (Strings.nonePrefixMatch(oldConfigMap.getMetadata().getNamespace(), OperatorConfig.INSTANCE.getNamespacePrefixes())
                && Strings.nonePrefixMatch(newConfigMap.getMetadata().getNamespace(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
                return;
            }

            String resourceLabel = annotationFor(newConfigMap);
            logger.debug("Received ADD event for \"{}\"", annotationFor(newConfigMap));

            processEvent(Watcher.Action.MODIFIED, newConfigMap);

            long endDateTime = System.currentTimeMillis();
            logger.info("ADD Event for {} processed in {}ms",
                    resourceLabel,
                    (endDateTime - startDateTime));
        }

        @Override
        public void onDelete(ConfigMap configMap, boolean deletedFinalStateUnknown) {
            long startDateTime = System.currentTimeMillis();

            if (Strings.nonePrefixMatch(configMap.getMetadata().getNamespace(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
                return;
            }

            String resourceLabel = annotationFor(configMap);
            logger.debug("Received ADD event for \"{}\"", annotationFor(configMap));


            long endDateTime = System.currentTimeMillis();
            logger.info("ADD Event for {} processed in {}ms",
                    resourceLabel,
                    (endDateTime - startDateTime));
        }
    }

    private String readRabbitMQPasswordForSchema(KubernetesClient client, String namespace, String secretName) throws Exception {

        Secret secret = client.secrets().inNamespace(namespace).withName(secretName).get();
        if (secret == null)
            throw new Exception(String.format("Secret not found \"%s\"",
                    annotationFor(namespace, "Secret", secretName)));
        if (secret.getData() == null)
            throw new Exception(String.format("Invalid secret \"%s\". No data",
                    annotationFor(secret)));

        String password = secret.getData().get(OperatorConfig.RABBITMQ_SECRET_PASSWORD_KEY);
        if (password == null)
            throw new Exception(String.format("Invalid secret \"%s\". No password was found with key \"%s\""
                    , annotationFor(secret)
                    , OperatorConfig.RABBITMQ_SECRET_PASSWORD_KEY));

        if (secret.getType().equals(SECRET_TYPE_OPAQUE))
            password = new String(Base64.getDecoder().decode(password.getBytes()));
        return password;
    }

    private class DictionaryEventHandler implements ResourceEventHandler <Th2Dictionary> {

        private Set<String> getLinkedResources(Th2Dictionary dictionary) {
            Set<String> resources = new HashSet<>();

            var lSingleton = OperatorState.INSTANCE;

            for (var linkRes : lSingleton.getLinkResources(extractNamespace(dictionary))) {
                for (var dicLink : linkRes.getSpec().getDictionariesRelation()) {
                    if (dicLink.getDictionary().getName().contains(extractName(dictionary))) {
                        resources.add(dicLink.getBox());
                    }
                }
            }

            return resources;
        }

        private void handleEvent (Watcher.Action action, Th2Dictionary dictionary) {
            String resourceLabel = annotationFor(dictionary);
            logger.info("Updating all boxes that contains dictionary \"{}\"", resourceLabel);

            var linkedResources = getLinkedResources(dictionary);

            logger.info("{} box(es) need updating", linkedResources.size());

            var refreshedBoxCount = refreshBoxes(extractNamespace(dictionary), linkedResources);

            logger.info("{} box-definition(s) updated", refreshedBoxCount);
        }

        @Override
        public void onAdd(Th2Dictionary dictionary) {
            handleEvent(Watcher.Action.ADDED, dictionary);
        }

        @Override
        public void onUpdate(Th2Dictionary oldDictionary, Th2Dictionary newDictionary) {
            handleEvent(Watcher.Action.MODIFIED, newDictionary);
        }

        @Override
        public void onDelete(Th2Dictionary dictionary, boolean deletedFinalStateUnknown) {
            handleEvent(Watcher.Action.DELETED, dictionary);
        }
    }

    private class LinkResourceEventHandler implements ResourceEventHandler<Th2Link> {

        private <T extends Identifiable> void checkForSameName(List<T> links, String annotation) {
            Set<String> linkNames = new HashSet<>();
            for (var link : links) {
                if (!linkNames.add(link.getName())) {
                    logger.warn("Link with name: {} already exists in {}", link.getName(), annotation);
                }
            }
        }


        @Override
        public void onAdd(Th2Link th2Link) {

            var linkNamespace = extractNamespace(th2Link);
            var lock = OperatorState.INSTANCE.getLock(linkNamespace);
            try {
                lock.lock();

                var linkSingleton = OperatorState.INSTANCE;

                var resourceLinks = new ArrayList<>(linkSingleton.getLinkResources(linkNamespace));

                var oldLinkRes = getOldLink(th2Link, resourceLinks);

                int refreshedBoxCount = 0;

                checkForSameName(th2Link.getSpec().getBoxesRelation().getRouterMq(), annotationFor(th2Link));
                checkForSameName(th2Link.getSpec().getBoxesRelation().getRouterGrpc(), annotationFor(th2Link));
                checkForSameName(th2Link.getSpec().getDictionariesRelation(), annotationFor(th2Link));

                logger.info("Updating all dependent boxes according to provided links ...");

                refreshedBoxCount = refreshBoxesIfNeeded(oldLinkRes, th2Link);
                resourceLinks.remove(th2Link);
                resourceLinks.add(th2Link);

                logger.info("{} box-definition(s) updated", refreshedBoxCount);

                linkSingleton.setLinkResources(linkNamespace, resourceLinks);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onUpdate(Th2Link oldTh2Link, Th2Link newTh2Link) {

            var linkNamespace = extractNamespace(newTh2Link);
            var lock = OperatorState.INSTANCE.getLock(linkNamespace);
            try {
                lock.lock();

                var linkSingleton = OperatorState.INSTANCE;

                var resourceLinks = new ArrayList<>(linkSingleton.getLinkResources(linkNamespace));

                var oldLinkRes = getOldLink(newTh2Link, resourceLinks);

                int refreshedBoxCount = 0;

                checkForSameName(newTh2Link.getSpec().getBoxesRelation().getRouterMq(), annotationFor(newTh2Link));
                checkForSameName(newTh2Link.getSpec().getBoxesRelation().getRouterGrpc(), annotationFor(newTh2Link));
                checkForSameName(newTh2Link.getSpec().getDictionariesRelation(), annotationFor(newTh2Link));


                logger.info("Updating all dependent boxes according to updated links ...");

                refreshedBoxCount = refreshBoxesIfNeeded(oldLinkRes, newTh2Link);

                resourceLinks.remove(newTh2Link);

                resourceLinks.add(newTh2Link);

                logger.info("{} box-definition(s) updated", refreshedBoxCount);

                linkSingleton.setLinkResources(linkNamespace, resourceLinks);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onDelete(Th2Link th2Link, boolean deletedFinalStateUnknown) {

            var linkNamespace = extractNamespace(th2Link);
            var lock = OperatorState.INSTANCE.getLock(linkNamespace);
            try {
                lock.lock();

                var linkSingleton = OperatorState.INSTANCE;

                var resourceLinks = new ArrayList<>(linkSingleton.getLinkResources(linkNamespace));

                var oldLinkRes = getOldLink(th2Link, resourceLinks);

                int refreshedBoxCount = 0;

                checkForSameName(th2Link.getSpec().getBoxesRelation().getRouterMq(), annotationFor(th2Link));
                checkForSameName(th2Link.getSpec().getBoxesRelation().getRouterGrpc(), annotationFor(th2Link));
                checkForSameName(th2Link.getSpec().getDictionariesRelation(), annotationFor(th2Link));


                logger.info("Updating all dependent boxes of destroyed links ...");

                refreshedBoxCount = refreshBoxesIfNeeded(oldLinkRes, Th2Link.newInstance());

                resourceLinks.remove(th2Link);

                //TODO: Error case not covered

                logger.info("{} box-definition(s) updated", refreshedBoxCount);

                linkSingleton.setLinkResources(linkNamespace, resourceLinks);
            } finally {
                lock.unlock();
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
