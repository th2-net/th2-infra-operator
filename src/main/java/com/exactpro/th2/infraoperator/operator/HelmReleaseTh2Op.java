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

package com.exactpro.th2.infraoperator.operator;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.DictionaryEntity;
import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.factory.DictionaryFactory;
import com.exactpro.th2.infraoperator.model.box.configuration.grpc.GrpcRouterConfiguration;
import com.exactpro.th2.infraoperator.model.box.configuration.grpc.factory.GrpcRouterConfigFactory;
import com.exactpro.th2.infraoperator.model.box.configuration.mq.MessageRouterConfiguration;
import com.exactpro.th2.infraoperator.model.box.configuration.mq.factory.MessageRouterConfigFactory;
import com.exactpro.th2.infraoperator.model.box.schema.link.EnqueuedLink;
import com.exactpro.th2.infraoperator.operator.context.HelmOperatorContext;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.Th2Spec;
import com.exactpro.th2.infraoperator.spec.helmRelease.HelmRelease;
import com.exactpro.th2.infraoperator.spec.helmRelease.HelmReleaseList;
import com.exactpro.th2.infraoperator.spec.helmRelease.HelmReleaseSecrets;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.DictionaryBinding;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCoupling;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingGRPC;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingMQ;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinMQ;
import com.exactpro.th2.infraoperator.spec.shared.PinAttribute;
import com.exactpro.th2.infraoperator.spec.shared.PinSpec;
import com.exactpro.th2.infraoperator.spec.shared.PrometheusConfiguration;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.dictionary.DictionaryLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.grpc.GrpcLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.mq.QueueLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.mq.impl.DeclareQueueResolver;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.box.BoxResourceFinder;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import com.exactpro.th2.infraoperator.util.JsonUtils;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.informers.SharedInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

public abstract class HelmReleaseTh2Op<CR extends Th2CustomResource> extends AbstractTh2Operator<CR, HelmRelease> {

    private static final Logger logger = LoggerFactory.getLogger(HelmReleaseTh2Op.class);

    public static final int PROPERTIES_MERGE_DEPTH = 1;

    public static final String CHART_PROPERTIES_ALIAS = "chart";
    public static final String ROOT_PROPERTIES_ALIAS = "component";
    public static final String EXTENDED_SETTINGS_ALIAS = "extendedSettings";
    public static final String MQ_CONFIG_ALIAS = "routerMq";
    public static final String CUSTOM_CONFIG_ALIAS = "custom";
    public static final String PROMETHEUS_CONFIG_ALIAS = "prometheus";
    public static final String SCHEMA_SECRETS_ALIAS = "secrets";
    public static final String GRPC_CONFIG_ALIAS = "grpcRouter";
    public static final String DICTIONARIES_ALIAS = "dictionaries";
    public static final String ANNOTATIONS_ALIAS = "annotations";
    public static final String DOCKER_IMAGE_ALIAS = "image";
    public static final String COMPONENT_NAME_ALIAS = "name";
    public static final String RELEASE_NAME_ALIAS = "releaseName";
    public static final String HELM_RELEASE_CRD_NAME = "helmreleases.helm.fluxcd.io";

    protected final BoxResourceFinder resourceFinder;
    protected final GrpcLinkResolver grpcLinkResolver;
    protected final QueueLinkResolver queueGenLinkResolver;
    protected final DictionaryLinkResolver dictionaryLinkResolver;
    protected final DeclareQueueResolver declareQueueResolver;
    protected final MessageRouterConfigFactory mqConfigFactory;
    protected final GrpcRouterConfigFactory grpcConfigFactory;
    protected final DictionaryFactory dictionaryFactory;

    protected final CustomResourceDefinition helmReleaseCrd;
    protected final MixedOperation<HelmRelease, HelmReleaseList, Resource<HelmRelease>> helmReleaseClient;

    protected final ActiveLinkUpdater activeLinkUpdaterOnDelete;
    protected final ActiveLinkUpdater activeLinkUpdaterOnAdd;
    protected final StorageTh2LinksRefresher msgStLinkUpdaterOnDelete;
    protected final StorageTh2LinksRefresher msgStLinkUpdaterOnAdd;
    protected final StorageTh2LinksRefresher eventStLinkUpdaterOnDelete;
    protected final StorageTh2LinksRefresher eventStLinkUpdaterOnAdd;

    public HelmReleaseTh2Op(HelmOperatorContext.Builder<?, ?> builder) {

        super(builder.getClient());

        this.resourceFinder = builder.getResourceFinder();
        this.grpcLinkResolver = builder.getGrpcLinkResolver();
        this.mqConfigFactory = builder.getMqConfigFactory();
        this.queueGenLinkResolver = builder.getQueueGenLinkResolver();
        this.declareQueueResolver = new DeclareQueueResolver();
        this.dictionaryLinkResolver = builder.getDictionaryLinkResolver();
        this.grpcConfigFactory = builder.getGrpcConfigFactory();
        this.dictionaryFactory = builder.getDictionaryFactory();

        helmReleaseCrd = CustomResourceUtils.getResourceCrd(kubClient, HELM_RELEASE_CRD_NAME);

        CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
            .withGroup(helmReleaseCrd.getSpec().getGroup())
            .withVersion(helmReleaseCrd.getSpec().getVersions().get(0).getName())
            .withScope(helmReleaseCrd.getSpec().getScope())
            .withPlural(helmReleaseCrd.getSpec().getNames().getPlural())
            .build();

        helmReleaseClient = kubClient.customResources(
            crdContext,
            HelmRelease.class,
            HelmReleaseList.class
        );

        var msgStContext = MsgStorageContext.builder()
            .linkResourceName(StoreHelmTh2Op.MSG_ST_LINK_RESOURCE_NAME)
            .linkNameSuffix(StoreHelmTh2Op.MESSAGE_STORAGE_LINK_NAME_SUFFIX)
            .boxAlias(StoreHelmTh2Op.MESSAGE_STORAGE_BOX_ALIAS)
            .pinName(StoreHelmTh2Op.MESSAGE_STORAGE_PIN_ALIAS)
            .build();

        var eventStContext = EventStorageContext.builder()
            .linkResourceName(StoreHelmTh2Op.EVENT_ST_LINK_RESOURCE_NAME)
            .linkNameSuffix(StoreHelmTh2Op.EVENT_STORAGE_LINK_NAME_SUFFIX)
            .boxAlias(StoreHelmTh2Op.EVENT_STORAGE_BOX_ALIAS)
            .pinName(StoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS)
            .build();

        this.msgStLinkUpdaterOnDelete = new StorageTh2LinksCleaner(msgStContext);
        this.msgStLinkUpdaterOnAdd = new StorageTh2LinksUpdater(msgStContext);
        this.eventStLinkUpdaterOnDelete = new StorageTh2LinksCleaner(eventStContext);
        this.eventStLinkUpdaterOnAdd = new StorageTh2LinksUpdater(eventStContext);
        this.activeLinkUpdaterOnDelete = new DeletedActiveLinkUpdater();
        this.activeLinkUpdaterOnAdd = new AddedActiveLinkUpdater();
    }

    public abstract SharedInformer<CR> generateInformerFromFactory (SharedInformerFactory factory);

    @Override
    protected void mapProperties(CR resource, HelmRelease helmRelease) {
        super.mapProperties(resource, helmRelease);

        String resNamespace = ExtractUtils.extractNamespace(resource);
        Th2Spec resSpec = resource.getSpec();
        OperatorState lSingleton = OperatorState.INSTANCE;
        var grpcActiveLinks = lSingleton.getGrpcActiveLinks(resNamespace);
        var dictionaryActiveLinks = lSingleton.getDictionaryActiveLinks(resNamespace);

        MessageRouterConfiguration mqConfig = mqConfigFactory.createConfig(resource);
        GrpcRouterConfiguration grpcConfig = grpcConfigFactory.createConfig(resource, grpcActiveLinks);
        List<DictionaryEntity> dictionaries = dictionaryFactory.create(resource, dictionaryActiveLinks);

        helmRelease.putSpecProp(RELEASE_NAME_ALIAS, ExtractUtils.extractNamespace(helmRelease) + "-" + ExtractUtils.extractName(helmRelease));
        helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS, Map.of(
            DOCKER_IMAGE_ALIAS, resSpec.getImageName() + ":" + resSpec.getImageVersion(),
            COMPONENT_NAME_ALIAS, resource.getMetadata().getName(),
            CUSTOM_CONFIG_ALIAS, resource.getSpec().getCustomConfig(),
            MQ_CONFIG_ALIAS, JsonUtils.writeValueAsDeepMap(mqConfig),
            GRPC_CONFIG_ALIAS, JsonUtils.writeValueAsDeepMap(grpcConfig)
        ));

        PrometheusConfiguration prometheusConfig = resource.getSpec().getPrometheusConfiguration();
        if (prometheusConfig == null)
            prometheusConfig = PrometheusConfiguration.createDefault();
        helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                Map.of(PROMETHEUS_CONFIG_ALIAS, prometheusConfig));

        if (!dictionaries.isEmpty())
            helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                Map.of(DICTIONARIES_ALIAS, dictionaries));

        Map<String, Object> extendedSettings = resSpec.getExtendedSettings();
        if (extendedSettings != null)
            helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                Map.of(EXTENDED_SETTINGS_ALIAS, extendedSettings));

        HelmReleaseSecrets secrets = new HelmReleaseSecrets(OperatorConfig.INSTANCE.getSchemaSecrets());
        helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
            Map.of(SCHEMA_SECRETS_ALIAS, secrets));

        var defaultChartConfig = OperatorConfig.INSTANCE.getChartConfig();
        var chartConfig = resSpec.getChartConfig();
        if (chartConfig != null) {
            defaultChartConfig = defaultChartConfig.overrideWith(chartConfig);
        }

        helmRelease.mergeSpecProp(CHART_PROPERTIES_ALIAS, defaultChartConfig.toMap());
        helmRelease.mergeValue(Map.of(ANNOTATIONS_ALIAS, ExtractUtils.extractAnnotations(resource).get(ANTECEDENT_LABEL_KEY_ALIAS)));
    }

    @Override
    protected void addedEvent(CR resource) {

        synchronized (OperatorState.INSTANCE.getLock(ExtractUtils.extractNamespace(resource))) {

            updateEventStorageLinksBeforeAdd(resource);

            updateMsgStorageLinksBeforeAdd(resource);

            var linkedResources = updateActiveLinksBeforeAdd(resource);

            updateDependedResourcesIfNeeded(resource, linkedResources);

            super.addedEvent(resource);

        }

    }

    @Override
    protected void modifiedEvent(CR resource) {

        synchronized (OperatorState.INSTANCE.getLock(ExtractUtils.extractNamespace(resource))) {

            updateEventStorageLinksBeforeAdd(resource);

            updateMsgStorageLinksBeforeAdd(resource);

            var linkedResources = updateActiveLinksBeforeAdd(resource);

            updateDependedResourcesIfNeeded(resource, linkedResources);

            super.modifiedEvent(resource);

        }

    }

    @Override
    protected void deletedEvent(CR resource) {

        synchronized (OperatorState.INSTANCE.getLock(ExtractUtils.extractNamespace(resource))) {

            super.deletedEvent(resource);

            updateEventStorageLinksAfterDelete(resource);

            updateMsgStorageLinksAfterDelete(resource);

            var linkedResources = updateActiveLinksAfterDelete(resource);

            updateDependedResourcesIfNeeded(resource, linkedResources);

        }

    }

    @Override
    protected void setupKubObj(CR resource, HelmRelease helmRelease) {
        super.setupKubObj(resource, helmRelease);

        if (!CustomResourceUtils.isResourceCrdExist(kubClient, helmReleaseCrd)) {
            String kubObjType = helmRelease.getClass().getSimpleName();
            CustomResourceUtils.createResourceCrd(kubClient, helmReleaseCrd, kubObjType);
        }

    }

    @Override
    protected void createKubObj(String namespace, HelmRelease helmRelease) {
        helmReleaseClient.inNamespace(namespace).createOrReplace(helmRelease);
    }

    @Override
    protected HelmRelease parseStreamToKubObj(InputStream stream) {
        return helmReleaseClient.load(stream).get();
    }

    protected List<Th2CustomResource> updateActiveLinksBeforeAdd(CR resource) {
        return activeLinkUpdaterOnAdd.updateLinks(resource);
    }

    protected void updateEventStorageLinksBeforeAdd(CR resource) {
        eventStLinkUpdaterOnAdd.updateStorageResLinks(resource);
    }

    protected void updateMsgStorageLinksBeforeAdd(CR resource) {
        msgStLinkUpdaterOnAdd.updateStorageResLinks(resource);
    }

    protected List<Th2CustomResource> updateActiveLinksAfterDelete(CR resource) {
        return activeLinkUpdaterOnDelete.updateLinks(resource);
    }

    protected void updateEventStorageLinksAfterDelete(CR resource) {
        eventStLinkUpdaterOnDelete.updateStorageResLinks(resource);
    }

    protected void updateMsgStorageLinksAfterDelete(CR resource) {
        msgStLinkUpdaterOnDelete.updateStorageResLinks(resource);
    }

    protected void updateDependedResourcesIfNeeded(CR resource, List<Th2CustomResource> linkedResources) {

        var currentResName = ExtractUtils.extractName(resource);

        var namespace = ExtractUtils.extractNamespace(resource);

        logger.info("Updating all linked boxes of '{}.{}' resource...", namespace, currentResName);

        var lSingleton = OperatorState.INSTANCE;

        var grpcActiveLinks = new ArrayList<>(lSingleton.getGrpcActiveLinks(namespace));

        var helmReleases = getAllHelmReleases(namespace);

        for (var res : linkedResources) {
            logger.debug("Linked resource: {}", CustomResourceUtils.annotationFor(res));

            var resourceName = ExtractUtils.extractName(res);

            var hr = getHelmRelease(res, helmReleases);
            if (hr == null) {
                logger.info("Release of '{}.{}' resource not found", namespace, resourceName);
                continue;
            } else {
                logger.debug("Found HelmRelease \"{}\"", CustomResourceUtils.annotationFor(hr));
            }

            var hrMqConfig = extractHelmReleaseMqConfig(hr);

            var hrRawGrpcConfig = extractHelmReleaseRawGrpcConfig(hr);

            var newResMqConfig = mqConfigFactory.createConfig(res);

            var newResGrpcConfig = grpcConfigFactory.createConfig(res, grpcActiveLinks);

            var newResRawGrpcConfig = JsonUtils.writeValueAsDeepMap(newResGrpcConfig);

            if (!newResMqConfig.equals(hrMqConfig) || !newResRawGrpcConfig.equals(hrRawGrpcConfig)) {
                logger.info("Updating helm release of '{}.{}' resource", namespace, resourceName);

                hr.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS, Map.of(
                    MQ_CONFIG_ALIAS, JsonUtils.writeValueAsDeepMap(newResMqConfig),
                    GRPC_CONFIG_ALIAS, newResRawGrpcConfig
                ));

                createKubObj(ExtractUtils.extractNamespace(hr), hr);
                logger.debug("Updated HelmRelease \"{}\"", CustomResourceUtils.annotationFor(hr));

            } else {
                logger.info("Resource '{}.{}' doesn't need updating", namespace, resourceName);
            }

        }
    }

    protected List<Th2CustomResource> getAllLinkedResources(Th2CustomResource resource, List<PinCoupling> links) {
        Map<String, Th2CustomResource> resources = new HashMap<>();

        var boxName = ExtractUtils.extractName(resource);
        var boxNamespace = ExtractUtils.extractNamespace(resource);

        for (var link : links) {
            var fromBoxName = link.getFrom().getBoxName();
            var toBoxName = link.getTo().getBoxName();

            if (fromBoxName.equals(boxName)) {
                addResourceIfExist(toBoxName, boxNamespace, resources);
            } else if (toBoxName.equals(boxName)) {
                addResourceIfExist(fromBoxName, boxNamespace, resources);
            }
        }

        return new ArrayList<>(resources.values());
    }

    private void addResourceIfExist(String name, String namespace, Map<String, Th2CustomResource> resources) {
        var alreadyAddedRes = resources.get(name);
        if (Objects.isNull(alreadyAddedRes)) {
            var resource = resourceFinder.getResource(name, namespace);
            if (Objects.nonNull(resource)) {
                resources.put(name, resource);
            }
        }
    }

    private HelmRelease getHelmRelease(Th2CustomResource resource, List<HelmRelease> helmReleases) {
        var resName = ExtractUtils.extractFullName(resource);
        return helmReleases.stream()
            .filter(hr -> {
                var owner = ExtractUtils.extractOwnerFullName(hr);
                return Objects.nonNull(owner) && owner.equals(resName);
            }).findFirst()
            .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private MessageRouterConfiguration extractHelmReleaseMqConfig(HelmRelease helmRelease) {
        var values = (Map<String, Object>) helmRelease.getValuesSection();
        var componentConfigs = (Map<String, Object>) values.get(ROOT_PROPERTIES_ALIAS);
        var mqConfig = (Map<String, Object>) componentConfigs.get(MQ_CONFIG_ALIAS);
        return JsonUtils.JSON_READER.convertValue(mqConfig, MessageRouterConfiguration.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractHelmReleaseRawGrpcConfig(HelmRelease helmRelease) {
        var values = (Map<String, Object>) helmRelease.getValuesSection();
        var componentConfigs = (Map<String, Object>) values.get(ROOT_PROPERTIES_ALIAS);
        return (Map<String, Object>) componentConfigs.get(GRPC_CONFIG_ALIAS);
    }

    private List<HelmRelease> getAllHelmReleases(String namespace) {
        return helmReleaseClient.inNamespace(namespace).list().getItems();
    }

    private abstract static class ActiveLinkUpdater {

        public List<Th2CustomResource> updateLinks(Th2CustomResource resource) {

            var resourceName = ExtractUtils.extractName(resource);

            var resNamespace = ExtractUtils.extractNamespace(resource);

            var lSingleton = OperatorState.INSTANCE;

            var linkResources = lSingleton.getLinkResources(resNamespace);

            var allActiveLinksOld = new ArrayList<>(lSingleton.getAllBoxesActiveLinks(resNamespace));

            var mqActiveLinks = new ArrayList<>(lSingleton.getMqActiveLinks(resNamespace));

            var grpcActiveLinks = new ArrayList<>(lSingleton.getGrpcActiveLinks(resNamespace));

            var dictionaryActiveLinks = new ArrayList<>(lSingleton.getDictionaryActiveLinks(resNamespace));

            var oldMsgStSize = lSingleton.getMsgStorageActiveLinks(resNamespace).size();

            var oldEventStSize = lSingleton.getEventStorageActiveLinks(resNamespace).size();

            var oldMqSize = lSingleton.getGeneralMqActiveLinks(resNamespace).size();

            var oldGrpcSize = grpcActiveLinks.size();

            var oldDicSize = dictionaryActiveLinks.size();

            refreshQueues(resource);

            refreshMqLinks(linkResources, mqActiveLinks, resource);

            refreshGrpcLinks(linkResources, grpcActiveLinks, resource);

            refreshDictionaryLinks(linkResources, dictionaryActiveLinks, resource);

            lSingleton.setMqActiveLinks(resNamespace, mqActiveLinks);

            lSingleton.setGrpcActiveLinks(resNamespace, grpcActiveLinks);

            lSingleton.setDictionaryActiveLinks(resNamespace, dictionaryActiveLinks);

            var newMsgStSize = lSingleton.getMsgStorageActiveLinks(resNamespace).size();

            var newEventStSize = lSingleton.getEventStorageActiveLinks(resNamespace).size();

            var newMqSize = lSingleton.getGeneralMqActiveLinks(resNamespace).size();

            var newGrpcSize = grpcActiveLinks.size();

            var newDicSize = dictionaryActiveLinks.size();

            var allActiveLinksNew = new ArrayList<>(lSingleton.getAllBoxesActiveLinks(resNamespace));

            var hiddenLinks = newMsgStSize + newEventStSize;

            var allSize = allActiveLinksNew.size() + newDicSize - hiddenLinks;

            logger.info(String.format(
                "Updated active links in namespace '%s'. Active links: %s (hidden %s). " +
                    "Details: %+d mq, %+d grpc, %+d dictionary, %+d hidden[mstore], %+d hidden[estore]",
                resNamespace, allSize, hiddenLinks,
                newMqSize - oldMqSize,
                newGrpcSize - oldGrpcSize,
                newDicSize - oldDicSize,
                newMsgStSize - oldMsgStSize,
                newEventStSize - oldEventStSize
            ));

            var linkedResources = getLinkedResources(resource, allActiveLinksOld, allActiveLinksNew);

            logger.info("Found {} linked resources of '{}.{}' resource", linkedResources.size(), resNamespace,
                resourceName);

            return linkedResources;
        }

        protected abstract void refreshQueues(Th2CustomResource resource);

        protected abstract void refreshMqLinks(
            List<Th2Link> linkResources,
            List<EnqueuedLink> activeLinks,
            Th2CustomResource... newResources
        );

        protected abstract void refreshGrpcLinks(
            List<Th2Link> linkResources,
            List<PinCouplingGRPC> activeLinks,
            Th2CustomResource... newResources
        );

        protected abstract void refreshDictionaryLinks(
            List<Th2Link> linkResources,
            List<DictionaryBinding> activeLinks,
            Th2CustomResource... newResources
        );

        protected abstract List<Th2CustomResource> getLinkedResources(
            Th2CustomResource resource,
            List<PinCoupling> oldLinks,
            List<PinCoupling> newLinks
        );
    }

    private class AddedActiveLinkUpdater extends ActiveLinkUpdater {

        @Override
        protected void refreshGrpcLinks(List<Th2Link> linkResources, List<PinCouplingGRPC> grpcActiveLinks,
                                        Th2CustomResource... newResources) {
            grpcLinkResolver.resolve(linkResources, grpcActiveLinks, newResources);
        }

        @Override
        protected void refreshMqLinks(List<Th2Link> linkResources, List<EnqueuedLink> mqActiveLinks,
                                      Th2CustomResource... newResources) {
            queueGenLinkResolver.resolve(linkResources, mqActiveLinks, newResources);
        }

        @Override
        protected void refreshDictionaryLinks(List<Th2Link> linkResources, List<DictionaryBinding> dicActiveLinks,
                                              Th2CustomResource... newResources) {
            dictionaryLinkResolver.resolve(linkResources, dicActiveLinks, newResources);
        }

        @Override
        protected List<Th2CustomResource> getLinkedResources(Th2CustomResource resource, List<PinCoupling> oldLinks,
                                                             List<PinCoupling> newLinks) {
            return getAllLinkedResources(resource, newLinks);
        }

        @Override
        protected void refreshQueues(Th2CustomResource resource) {
            declareQueueResolver.resolveAdd(resource);
        }
    }

    private class DeletedActiveLinkUpdater extends ActiveLinkUpdater {

        @Override
        protected void refreshGrpcLinks(List<Th2Link> linkResources, List<PinCouplingGRPC> grpcActiveLinks,
                                        Th2CustomResource... newResources) {
            grpcLinkResolver.resolve(linkResources, grpcActiveLinks);
        }

        @Override
        protected void refreshMqLinks(List<Th2Link> linkResources, List<EnqueuedLink> mqActiveLinks,
                                      Th2CustomResource... newResources) {
            queueGenLinkResolver.resolve(linkResources, mqActiveLinks);
        }

        @Override
        protected void refreshDictionaryLinks(List<Th2Link> linkResources, List<DictionaryBinding> dicActiveLinks,
                                              Th2CustomResource... newResources) {
            dictionaryLinkResolver.resolve(linkResources, dicActiveLinks);
        }

        @Override
        protected List<Th2CustomResource> getLinkedResources(Th2CustomResource resource, List<PinCoupling> oldLinks,
                                                             List<PinCoupling> newLinks) {
            return getAllLinkedResources(resource, oldLinks);
        }

        @Override
        protected void refreshQueues(Th2CustomResource resource) {
            declareQueueResolver.resolveDelete(resource);
        }
    }

    private abstract class StorageTh2LinksRefresher {

        private StorageContext context;

        public StorageTh2LinksRefresher(StorageContext context) {
            this.context = context;
        }

        public void updateStorageResLinks(CR resource) {

            var resName = ExtractUtils.extractName(resource);

            var resNamespace = ExtractUtils.extractNamespace(resource);

            var lSingleton = OperatorState.INSTANCE;

            var linkResources = new ArrayList<>(lSingleton.getLinkResources(resNamespace));

            var hiddenLinksRes = getStLinkResAndCreateIfAbsent(resNamespace, linkResources);

            var oldHiddenLinks = hiddenLinksRes.getSpec().getBoxesRelation().getRouterMq();

            var newHiddenLinks = createHiddenLinks(resource);

            var updatedHiddenLinks = update(oldHiddenLinks, newHiddenLinks);

            hiddenLinksRes.getSpec().getBoxesRelation().setRouterMq(updatedHiddenLinks);

            OperatorState.INSTANCE.setLinkResources(resNamespace, linkResources);

            logger.info("{} hidden links has been refreshed successfully with '{}.{}'", context.getBoxAlias(),
                resNamespace, resName);

        }

        @SneakyThrows
        protected Th2Link getStLinkResAndCreateIfAbsent(String namespace, List<Th2Link> linkResources) {

            var linkResourceName = context.getLinkResourceName();

            var hiddenLinksRes = linkResources.stream()
                .filter(lr -> ExtractUtils.extractName(lr).equals(linkResourceName))
                .findFirst()
                .orElse(null);

            if (Objects.isNull(hiddenLinksRes)) {
                hiddenLinksRes = Th2Link.newInstance();

                var hlMetadata = hiddenLinksRes.getMetadata();

                hlMetadata.setName(linkResourceName);
                hlMetadata.setNamespace(namespace);

                linkResources.add(hiddenLinksRes);
            }

            return hiddenLinksRes;
        }

        protected PinMQ createToBoxOfHiddenLink(PinSpec pin) {
            var hyphen = "-";
            var targetAttr = "";

            if (pin.getAttributes().contains(PinAttribute.parsed.name())) {
                targetAttr += hyphen + PinAttribute.parsed.name();
            } else if (pin.getAttributes().contains(PinAttribute.raw.name())) {
                targetAttr += hyphen + PinAttribute.raw.name();
            }

            return new PinMQ(context.getBoxAlias(), context.getPinName() + targetAttr);
        }

        protected PinCouplingMQ createHiddenLink(PinMQ fromBox, PinMQ toBox) {
            return new PinCouplingMQ(fromBox.toString() + context.getLinkNameSuffix(), fromBox, toBox);
        }

        protected List<PinCouplingMQ> createHiddenLinks(Th2CustomResource resource) {

            List<PinCouplingMQ> links = new ArrayList<>();

            for (var pin : resource.getSpec().getPins()) {

                if (context.checkAttributes(pin.getAttributes())) {

                    var fromLink = new PinMQ(ExtractUtils.extractName(resource), pin.getName());

                    var toLink = createToBoxOfHiddenLink(pin);

                    links.add(createHiddenLink(fromLink, toLink));

                }

            }

            return links;
        }

        protected abstract List<PinCouplingMQ> update(List<PinCouplingMQ> oldHiddenLinks, List<PinCouplingMQ> newHiddenLinks);

    }

    private class StorageTh2LinksCleaner extends StorageTh2LinksRefresher {

        public StorageTh2LinksCleaner(StorageContext context) {
            super(context);
        }

        @Override
        protected List<PinCouplingMQ> update(List<PinCouplingMQ> oldHiddenLinks, List<PinCouplingMQ> newHiddenLinks) {
            List<PinCouplingMQ> updated = new ArrayList<>(oldHiddenLinks);
            updated.removeAll(newHiddenLinks);
            return updated;
        }

    }

    private class StorageTh2LinksUpdater extends StorageTh2LinksRefresher {

        public StorageTh2LinksUpdater(StorageContext context) {
            super(context);
        }

        @Override
        protected List<PinCouplingMQ> update(List<PinCouplingMQ> oldHiddenLinks, List<PinCouplingMQ> newHiddenLinks) {
            List<PinCouplingMQ> updated = new ArrayList<>(oldHiddenLinks);

            for (var newLink : newHiddenLinks) {
                if (!updated.contains(newLink)) {
                    updated.add(newLink);
                }
            }

            return updated;
        }

    }

    @Getter
    @SuperBuilder
    private abstract static class StorageContext {

        private String linkResourceName;

        private String linkNameSuffix;

        private String boxAlias;

        private String pinName;

        protected abstract boolean checkAttributes(Set<String> attributes);

    }

    @Getter
    @SuperBuilder
    private static class MsgStorageContext extends StorageContext {

        @Override
        protected boolean checkAttributes(Set<String> attributes) {
            return attributes.contains(PinAttribute.store.name())
                && attributes.contains(PinAttribute.publish.name())
                && (attributes.contains(PinAttribute.parsed.name())
                || attributes.contains(PinAttribute.raw.name()))
                && !attributes.contains(PinAttribute.subscribe.name());
        }

    }

    @Getter
    @SuperBuilder
    private static class EventStorageContext extends StorageContext {

        @Override
        protected boolean checkAttributes(Set<String> attributes) {
            return attributes.contains(PinAttribute.event.name())
                && attributes.contains(PinAttribute.publish.name())
                && !attributes.contains(PinAttribute.subscribe.name());
        }

    }
}
