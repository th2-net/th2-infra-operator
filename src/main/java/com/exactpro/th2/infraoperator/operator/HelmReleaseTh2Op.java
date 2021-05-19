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
import com.exactpro.th2.infraoperator.operator.context.HelmOperatorContext;
import com.exactpro.th2.infraoperator.operator.helm.*;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.Th2Spec;
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease;
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmReleaseSecrets;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCoupling;
import com.exactpro.th2.infraoperator.spec.shared.PrometheusConfiguration;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.dictionary.DictionaryLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.grpc.GrpcLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.QueueLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.impl.DeclareQueueResolver;
import com.exactpro.th2.infraoperator.spec.strategy.resfinder.box.BoxResourceFinder;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import com.exactpro.th2.infraoperator.util.JsonUtils;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.SharedInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

import static com.exactpro.th2.infraoperator.util.ExtendedSettingsUtils.convertField;

public abstract class HelmReleaseTh2Op<CR extends Th2CustomResource> extends AbstractTh2Operator<CR, HelmRelease> {

    private static final Logger logger = LoggerFactory.getLogger(HelmReleaseTh2Op.class);

    public static final int PROPERTIES_MERGE_DEPTH = 1;

    public static final String CHART_PROPERTIES_ALIAS = "chart";

    public static final String ROOT_PROPERTIES_ALIAS = "component";

    public static final String EXTENDED_SETTINGS_ALIAS = "extendedSettings";

    private static final String SERVICE_ALIAS = "service";

    private static final String EXTERNAL_BOX_ALIAS = "externalBox";

    private static final String SHARED_MEMORY_ALIAS = "sharedMemory";

    private static final String ENABLED_ALIAS = "enabled";

    public static final String MQ_CONFIG_ALIAS = "routerMq";

    public static final String CUSTOM_CONFIG_ALIAS = "custom";

    public static final String PROMETHEUS_CONFIG_ALIAS = "prometheus";

    public static final String SCHEMA_SECRETS_ALIAS = "secrets";

    public static final String GRPC_CONFIG_ALIAS = "grpcRouter";

    public static final String DICTIONARIES_ALIAS = "dictionaries";

    public static final String LOGGING_ALIAS = "logging";

    public static final String ANNOTATIONS_ALIAS = "annotations";

    public static final String DOCKER_IMAGE_ALIAS = "image";

    public static final String COMPONENT_NAME_ALIAS = "name";

    public static final String RELEASE_NAME_ALIAS = "releaseName";

    public static final String INGRESS_HOST_ALIAS = "ingressHost";

    public static final String DEFAULT_VALUE_ENABLED = Boolean.TRUE.toString();

    protected final BoxResourceFinder resourceFinder;

    protected final GrpcLinkResolver grpcLinkResolver;

    protected final QueueLinkResolver queueGenLinkResolver;

    protected final DictionaryLinkResolver dictionaryLinkResolver;

    protected final DeclareQueueResolver declareQueueResolver;

    protected final MessageRouterConfigFactory mqConfigFactory;

    protected final GrpcRouterConfigFactory grpcConfigFactory;

    protected final DictionaryFactory dictionaryFactory;

    protected final MixedOperation<HelmRelease, KubernetesResourceList<HelmRelease>, Resource<HelmRelease>>
            helmReleaseClient;

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

        helmReleaseClient = kubClient.customResources(HelmRelease.class);

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
        this.activeLinkUpdaterOnDelete = new DeletedActiveLinkUpdater(this, grpcLinkResolver,
                queueGenLinkResolver, dictionaryLinkResolver, declareQueueResolver);
        this.activeLinkUpdaterOnAdd = new AddedActiveLinkUpdater(this, grpcLinkResolver,
                queueGenLinkResolver, dictionaryLinkResolver, declareQueueResolver);
    }

    public abstract SharedInformer<CR> generateInformerFromFactory(SharedInformerFactory factory);

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
        String loggingConfigChecksum = OperatorState.INSTANCE.getLoggingConfigChecksum(resNamespace);
        loggingConfigChecksum = loggingConfigChecksum != null ? loggingConfigChecksum : "";

        helmRelease.putSpecProp(RELEASE_NAME_ALIAS,
                ExtractUtils.extractNamespace(helmRelease) + "-" + ExtractUtils.extractName(helmRelease));
        helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS, Map.of(
                DOCKER_IMAGE_ALIAS, resSpec.getImageName() + ":" + resSpec.getImageVersion(),
                COMPONENT_NAME_ALIAS, resource.getMetadata().getName(),
                CUSTOM_CONFIG_ALIAS, resource.getSpec().getCustomConfig(),
                MQ_CONFIG_ALIAS, JsonUtils.writeValueAsDeepMap(mqConfig),
                GRPC_CONFIG_ALIAS, JsonUtils.writeValueAsDeepMap(grpcConfig)
        ));

        PrometheusConfiguration<String> prometheusConfig = resource.getSpec().getPrometheusConfiguration();
        if (prometheusConfig == null) {
            prometheusConfig = PrometheusConfiguration.createDefault(DEFAULT_VALUE_ENABLED);
        }
        PrometheusConfiguration<Boolean> prometheusConfigForRelease = PrometheusConfiguration.<Boolean>builder()
                .port(prometheusConfig.getPort())
                .host(prometheusConfig.getHost())
                .enabled(Boolean.valueOf(prometheusConfig.getEnabled()))
                .build();
        helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                Map.of(PROMETHEUS_CONFIG_ALIAS, prometheusConfigForRelease));

        if (!dictionaries.isEmpty()) {
            helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                    Map.of(DICTIONARIES_ALIAS, dictionaries));
        }

        helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                Map.of(LOGGING_ALIAS, loggingConfigChecksum));

        Map<String, Object> extendedSettings = resSpec.getExtendedSettings();
        if (extendedSettings != null) {
            helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                    Map.of(EXTENDED_SETTINGS_ALIAS, extendedSettings));
        }

        String ingressHost = OperatorConfig.INSTANCE.getIngressHost();
        if (ingressHost != null && !ingressHost.isEmpty()) {
            helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                    Map.of(INGRESS_HOST_ALIAS, ingressHost));
        }

        HelmReleaseSecrets secrets = new HelmReleaseSecrets(OperatorConfig.INSTANCE.getSchemaSecrets());
        helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                Map.of(SCHEMA_SECRETS_ALIAS, secrets));

        var defaultChartConfig = OperatorConfig.INSTANCE.getComponentChartConfig();
        var chartConfig = resSpec.getChartConfig();
        if (chartConfig != null) {
            defaultChartConfig = defaultChartConfig.overrideWith(chartConfig);
        }

        helmRelease.mergeSpecProp(CHART_PROPERTIES_ALIAS, defaultChartConfig.toMap());
        helmRelease.mergeValue(Map.of(ANNOTATIONS_ALIAS,
                ExtractUtils.extractAnnotations(resource).get(ANTECEDENT_LABEL_KEY_ALIAS)));

        convertField(helmRelease, Boolean::valueOf, ENABLED_ALIAS, ROOT_PROPERTIES_ALIAS,
                EXTENDED_SETTINGS_ALIAS, SERVICE_ALIAS);
        convertField(helmRelease, Boolean::valueOf, ENABLED_ALIAS, ROOT_PROPERTIES_ALIAS,
                EXTENDED_SETTINGS_ALIAS, EXTERNAL_BOX_ALIAS);
        convertField(helmRelease, Boolean::valueOf, ENABLED_ALIAS, ROOT_PROPERTIES_ALIAS,
                EXTENDED_SETTINGS_ALIAS, SHARED_MEMORY_ALIAS);
    }

    @Override
    protected void addedEvent(CR resource) {

        var lock = OperatorState.INSTANCE.getLock(ExtractUtils.extractNamespace(resource));
        try {
            lock.lock();

            updateEventStorageLinksBeforeAdd(resource);
            updateMsgStorageLinksBeforeAdd(resource);
            OperatorState.INSTANCE.addActiveTh2Resource(resource.getMetadata().getNamespace(),
                    resource.getMetadata().getName());
            var linkedResources = updateActiveLinksBeforeAdd(resource);
            updateDependedResourcesIfNeeded(resource, linkedResources);
            super.addedEvent(resource);

        } finally {
            lock.unlock();
        }

    }

    @Override
    protected void modifiedEvent(CR resource) {

        var lock = OperatorState.INSTANCE.getLock(ExtractUtils.extractNamespace(resource));
        try {
            lock.lock();

            updateEventStorageLinksBeforeAdd(resource);
            updateMsgStorageLinksBeforeAdd(resource);
            OperatorState.INSTANCE.addActiveTh2Resource(resource.getMetadata().getNamespace(),
                    resource.getMetadata().getName());
            var linkedResources = updateActiveLinksBeforeAdd(resource);
            updateDependedResourcesIfNeeded(resource, linkedResources);
            super.modifiedEvent(resource);

        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void deletedEvent(CR resource) {

        var lock = OperatorState.INSTANCE.getLock(ExtractUtils.extractNamespace(resource));
        try {
            lock.lock();

            OperatorState.INSTANCE.removeActiveResource(resource.getMetadata().getNamespace(),
                    resource.getMetadata().getName());
            super.deletedEvent(resource);
            updateEventStorageLinksAfterDelete(resource);
            updateMsgStorageLinksAfterDelete(resource);
            var linkedResources = updateActiveLinksAfterDelete(resource);
            updateDependedResourcesIfNeeded(resource, linkedResources);

        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void setupKubObj(CR resource, HelmRelease helmRelease) {
        super.setupKubObj(resource, helmRelease);
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

    public List<Th2CustomResource> getAllLinkedResources(Th2CustomResource resource, List<PinCoupling> links) {
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
        String resFullName = ExtractUtils.extractFullName(resource);
        String resName = hashNameIfNeeded(resFullName);
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

}
