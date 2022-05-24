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
import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.MultiDictionaryEntity;
import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.factory.DictionaryFactory;
import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.factory.MultiDictionaryFactory;
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
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingGRPC;
import com.exactpro.th2.infraoperator.spec.shared.PrometheusConfiguration;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.DeclareQueueResolver;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.SharedInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.mergeConfigs;
import static com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.BindQueueLinkResolver.resolveBoxResource;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.*;
import static com.exactpro.th2.infraoperator.util.HelmReleaseUtils.*;

public abstract class HelmReleaseTh2Op<CR extends Th2CustomResource> extends AbstractTh2Operator<CR, HelmRelease> {

    private static final Logger logger = LoggerFactory.getLogger(HelmReleaseTh2Op.class);

    public static final int PROPERTIES_MERGE_DEPTH = 1;

    public static final String ANTECEDENT_LABEL_KEY_ALIAS = "th2.exactpro.com/antecedent";

    public static final String COMMIT_HASH_LABEL_KEY_ALIAS = "th2.exactpro.com/git-commit-hash";

    //spec section
    private static final String CHART_PROPERTIES_ALIAS = "chart";

    private static final String OPENSHIFT_ALIAS = "openshift";

    public static final String RELEASE_NAME_ALIAS = "releaseName";

    //values section
    public static final String ROOT_PROPERTIES_ALIAS = "component";

    public static final String ANNOTATIONS_ALIAS = "commonAnnotations";

    //component section
    private static final String COMPONENT_NAME_ALIAS = "name";

    private static final String DOCKER_IMAGE_ALIAS = "image";

    private static final String CUSTOM_CONFIG_ALIAS = "custom";

    private static final String SECRET_VALUES_CONFIG_ALIAS = "secretValuesConfig";

    private static final String SECRET_PATHS_CONFIG_ALIAS = "secretPathsConfig";

    private static final String PROMETHEUS_CONFIG_ALIAS = "prometheus";

    public static final String DICTIONARIES_ALIAS = "oldDictionaries";

    public static final String MULTI_DICTIONARIES_ALIAS = "dictionaries";

    public static final String MQ_QUEUE_CONFIG_ALIAS = "mq";

    private static final String GRPC_P2P_CONFIG_ALIAS = "grpc";

    public static final String MQ_ROUTER_ALIAS = "mqRouter";

    public static final String GRPC_ROUTER_ALIAS = "grpcRouter";

    public static final String CRADLE_MGR_ALIAS = "cradleManager";

    public static final String BOOK_CONFIG_ALIAS = "bookConfig";

    public static final String BOOK_NAME_ALIAS = "bookName";

    public static final String LOGGING_ALIAS = "logging";

    public static final String SCHEMA_SECRETS_ALIAS = "secrets";

    public static final String INGRESS_ALIAS = "ingress";

    public static final String PULL_SECRETS_ALIAS = "imagePullSecrets";

    public static final String EXTENDED_SETTINGS_ALIAS = "extendedSettings";

    //extended settings section
    public static final String SERVICE_ALIAS = "service";

    public static final String EXTERNAL_BOX_ALIAS = "externalBox";

    //general aliases
    public static final String CONFIG_ALIAS = "config";

    public static final String CHECKSUM_ALIAS = "checksum";

    public static final String ENABLED_ALIAS = "enabled";

    private static final String DEFAULT_VALUE_ENABLED = Boolean.TRUE.toString();

    protected final DeclareQueueResolver declareQueueResolver;

    protected final MessageRouterConfigFactory mqConfigFactory;

    protected final GrpcRouterConfigFactory grpcConfigFactory;

    protected final DictionaryFactory dictionaryFactory;

    protected final MultiDictionaryFactory multiDictionaryFactory;

    protected final MixedOperation<HelmRelease, KubernetesResourceList<HelmRelease>, Resource<HelmRelease>>
            helmReleaseClient;

    protected final StorageTh2LinksRefresher msgStLinkUpdaterOnDelete;

    protected final StorageTh2LinksRefresher msgStLinkUpdaterOnAdd;

    protected final StorageTh2LinksRefresher eventStLinkUpdaterOnDelete;

    protected final StorageTh2LinksRefresher eventStLinkUpdaterOnAdd;

    public HelmReleaseTh2Op(HelmOperatorContext.Builder<?, ?> builder) {

        super(builder.getClient());

        this.mqConfigFactory = builder.getMqConfigFactory();
        this.declareQueueResolver = new DeclareQueueResolver();
        this.grpcConfigFactory = builder.getGrpcConfigFactory();
        this.dictionaryFactory = builder.getDictionaryFactory();
        this.multiDictionaryFactory = builder.getMultiDictionaryFactory();

        helmReleaseClient = kubClient.resources(HelmRelease.class);

        var msgStContext = new MsgStorageContext(
                StoreHelmTh2Op.MSG_ST_LINK_RESOURCE_NAME,
                StoreHelmTh2Op.MESSAGE_STORAGE_LINK_NAME_SUFFIX,
                StoreHelmTh2Op.MESSAGE_STORAGE_BOX_ALIAS,
                StoreHelmTh2Op.MESSAGE_STORAGE_PIN_ALIAS
        );

        var eventStContext = new EventStorageContext(
                StoreHelmTh2Op.EVENT_ST_LINK_RESOURCE_NAME,
                StoreHelmTh2Op.EVENT_STORAGE_LINK_NAME_SUFFIX,
                StoreHelmTh2Op.EVENT_STORAGE_BOX_ALIAS,
                StoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS
        );

        this.msgStLinkUpdaterOnDelete = new StorageTh2LinksCleaner(msgStContext);
        this.msgStLinkUpdaterOnAdd = new StorageTh2LinksUpdater(msgStContext);
        this.eventStLinkUpdaterOnDelete = new StorageTh2LinksCleaner(eventStContext);
        this.eventStLinkUpdaterOnAdd = new StorageTh2LinksUpdater(eventStContext);
    }

    public abstract SharedInformer<CR> generateInformerFromFactory(SharedInformerFactory factory);

    @Override
    protected void mapProperties(CR resource, HelmRelease helmRelease) {
        super.mapProperties(resource, helmRelease);

        String resNamespace = extractNamespace(resource);
        Th2Spec resSpec = resource.getSpec();
        OperatorState operatorState = OperatorState.INSTANCE;
        var grpcActiveLinks = operatorState.getGrpLinks(resNamespace);
        var dictionaryActiveLinks = operatorState.getDictionaryLinks(resNamespace);
        var multiDictionaryActiveLinks = operatorState.getMultiDictionaryLinks(resNamespace);

        MessageRouterConfiguration mqConfig = mqConfigFactory.createConfig(resource);
        GrpcRouterConfiguration grpcConfig = grpcConfigFactory.createConfig(resource, grpcActiveLinks);
        Collection<DictionaryEntity> dictionaries = dictionaryFactory.create(resource, dictionaryActiveLinks);
        List<MultiDictionaryEntity> multiDict = multiDictionaryFactory.create(resource, multiDictionaryActiveLinks);

        String loggingConfigChecksum = operatorState.getConfigChecksum(resNamespace, LOGGING_ALIAS);
        String mqRouterChecksum = operatorState.getConfigChecksum(resNamespace, MQ_ROUTER_ALIAS);
        String grpcRouterChecksum = operatorState.getConfigChecksum(resNamespace, GRPC_ROUTER_ALIAS);
        String cradleManagerChecksum = operatorState.getConfigChecksum(resNamespace, CRADLE_MGR_ALIAS);
        String defaultBookName = operatorState.getBookName(resNamespace);


        helmRelease.putSpecProp(RELEASE_NAME_ALIAS, extractNamespace(helmRelease) + "-" + extractName(helmRelease));

        String logFile = resource.getSpec().getLoggingConfig();
        Map<String, Object> logFileSection = new HashMap<>();
        logFileSection.put(CONFIG_ALIAS, logFile);
        logFileSection.put(CHECKSUM_ALIAS, loggingConfigChecksum);

        Map<String, Object> mqRouterSection = new HashMap<>();
        Map<String, Object> grpcRouterSection = new HashMap<>();
        Map<String, Object> cradleManagerSection = new HashMap<>();

        try {
            Map<String, Object> mqRouterConfig = resource.getSpec().getMqRouter();
            if (mqRouterConfig != null) {
                mqRouterSection.put(CONFIG_ALIAS,
                        mergeConfigs(operatorState.getConfigData(resNamespace, MQ_ROUTER_ALIAS), mqRouterConfig));
            } else {
                mqRouterSection.put(CONFIG_ALIAS, null);
            }
            mqRouterSection.put(CHECKSUM_ALIAS, mqRouterChecksum);

            Map<String, Object> grpcRouterConfig = resource.getSpec().getGrpcRouter();
            if (grpcRouterConfig != null) {
                grpcRouterSection.put(CONFIG_ALIAS,
                        mergeConfigs(operatorState.getConfigData(resNamespace, GRPC_ROUTER_ALIAS), grpcRouterConfig));
            } else {
                grpcRouterSection.put(CONFIG_ALIAS, null);
            }
            grpcRouterSection.put(CHECKSUM_ALIAS, grpcRouterChecksum);

            Map<String, Object> cradleManagerConfig = resource.getSpec().getCradleManager();
            if (cradleManagerConfig != null) {
                cradleManagerSection.put(CONFIG_ALIAS,
                        mergeConfigs(operatorState.getConfigData(resNamespace, CRADLE_MGR_ALIAS), cradleManagerConfig));
            } else {
                cradleManagerSection.put(CONFIG_ALIAS, null);
            }
            cradleManagerSection.put(CHECKSUM_ALIAS, cradleManagerChecksum);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        String crBookName = resource.getSpec().getBookName();
        Map<String, String> bookConfigSection = new HashMap<>();
        bookConfigSection.put(BOOK_NAME_ALIAS, crBookName != null ? crBookName : defaultBookName);

        HelmReleaseSecrets secrets = new HelmReleaseSecrets(OperatorConfig.INSTANCE.getSchemaSecrets());

        helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS, Map.of(
                DOCKER_IMAGE_ALIAS, resSpec.getImageName() + ":" + resSpec.getImageVersion(),
                COMPONENT_NAME_ALIAS, resource.getMetadata().getName(),
                MQ_QUEUE_CONFIG_ALIAS, JsonUtils.writeValueAsDeepMap(mqConfig),
                GRPC_P2P_CONFIG_ALIAS, JsonUtils.writeValueAsDeepMap(grpcConfig),
                LOGGING_ALIAS, logFileSection,
                MQ_ROUTER_ALIAS, mqRouterSection,
                GRPC_ROUTER_ALIAS, grpcRouterSection,
                CRADLE_MGR_ALIAS, cradleManagerSection,
                BOOK_CONFIG_ALIAS, bookConfigSection,
                SCHEMA_SECRETS_ALIAS, secrets
        ));

        Map<String, String> secretValuesConfig = new HashMap<>();
        Map<String, String> secretPathsConfig = new HashMap<>();
        generateSecretsConfig(resource.getSpec().getCustomConfig(), secretValuesConfig, secretPathsConfig);
        helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS, Map.of(
                CUSTOM_CONFIG_ALIAS, resource.getSpec().getCustomConfig(),
                SECRET_VALUES_CONFIG_ALIAS, secretValuesConfig,
                SECRET_PATHS_CONFIG_ALIAS, secretPathsConfig
        ));

        PrometheusConfiguration<String> prometheusConfig = resource.getSpec().getPrometheusConfiguration();
        if (prometheusConfig == null) {
            prometheusConfig = PrometheusConfiguration.createDefault(DEFAULT_VALUE_ENABLED);
        }

        PrometheusConfiguration<Boolean> prometheusConfigForRelease = new PrometheusConfiguration<>(
                prometheusConfig.getHost(),
                prometheusConfig.getPort(),
                Boolean.valueOf(prometheusConfig.getEnabled())
        );

        helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                Map.of(PROMETHEUS_CONFIG_ALIAS, prometheusConfigForRelease));

        if (!dictionaries.isEmpty()) {
            helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                    Map.of(DICTIONARIES_ALIAS, dictionaries));
        }

        if (!multiDict.isEmpty()) {
            helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                    Map.of(MULTI_DICTIONARIES_ALIAS, multiDict));
        }

        Map<String, Object> extendedSettings = resSpec.getExtendedSettings();
        if (extendedSettings != null) {
            helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                    Map.of(EXTENDED_SETTINGS_ALIAS, extendedSettings));
        }

        Object ingress = OperatorConfig.INSTANCE.getIngress();
        if (ingress != null) {
            helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                    Map.of(INGRESS_ALIAS, ingress));
        }

        var defaultChartConfig = OperatorConfig.INSTANCE.getComponentChartConfig();
        var chartConfig = resSpec.getChartConfig();
        if (chartConfig != null) {
            defaultChartConfig = defaultChartConfig.overrideWith(chartConfig);
        }

        helmRelease.mergeSpecProp(CHART_PROPERTIES_ALIAS, defaultChartConfig.toMap());

        var annotations = OperatorConfig.INSTANCE.getCommonAnnotations();
        annotations.putAll(extractNeededAnnotations(resource, ANTECEDENT_LABEL_KEY_ALIAS, COMMIT_HASH_LABEL_KEY_ALIAS));
        helmRelease.mergeValue(Map.of(
                ANNOTATIONS_ALIAS, annotations,
                OPENSHIFT_ALIAS, OperatorConfig.INSTANCE.getOpenshift()
        ));

        helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                Map.of(PULL_SECRETS_ALIAS, OperatorConfig.INSTANCE.getImagePullSecrets()));

        convertBooleanFields(helmRelease);
    }

    @Override
    protected void addedEvent(CR resource) throws IOException {

        String namespace = extractNamespace(resource);
        var lock = OperatorState.INSTANCE.getLock(namespace);
        try {
            lock.lock();

            updateEventStorageLinksBeforeAdd(resource);
            updateMsgStorageLinksBeforeAdd(resource);
            declareQueueResolver.resolveAdd(resource);
            resolveBoxResource(namespace, OperatorState.INSTANCE.getLinkResources(namespace), resource);
            updateGrpcLinkedResourcesIfNeeded(resource);
            super.addedEvent(resource);
        } finally {
            lock.unlock();
        }

    }

    @Override
    protected void modifiedEvent(CR resource) throws IOException {

        String namespace = extractNamespace(resource);
        var lock = OperatorState.INSTANCE.getLock(namespace);
        try {
            lock.lock();

            updateEventStorageLinksBeforeAdd(resource);
            updateMsgStorageLinksBeforeAdd(resource);
            declareQueueResolver.resolveAdd(resource);
            resolveBoxResource(namespace, OperatorState.INSTANCE.getLinkResources(namespace), resource);
            updateGrpcLinkedResourcesIfNeeded(resource);
            super.modifiedEvent(resource);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void deletedEvent(CR resource) {

        var lock = OperatorState.INSTANCE.getLock(extractNamespace(resource));
        try {
            lock.lock();
            super.deletedEvent(resource);
            updateEventStorageLinksAfterDelete(resource);
            updateMsgStorageLinksAfterDelete(resource);
            declareQueueResolver.resolveDelete(resource);
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
        String hrName = extractName(helmRelease);
        HelmRelease existingRelease = helmReleaseClient.inNamespace(namespace).withName(hrName).get();

        if (needsToBeDeleted(helmRelease, existingRelease)) {
            helmReleaseClient.inNamespace(namespace).withName(hrName).delete();
        }
        helmReleaseClient.inNamespace(namespace).createOrReplace(helmRelease);
        OperatorState.INSTANCE.putHelmReleaseInCache(helmRelease, namespace);
    }

    @Override
    protected HelmRelease parseStreamToKubObj(InputStream stream) {
        return helmReleaseClient.load(stream).get();
    }

    protected void updateEventStorageLinksBeforeAdd(CR resource) {
        eventStLinkUpdaterOnAdd.updateStorageResLinks(resource);
    }

    protected void updateMsgStorageLinksBeforeAdd(CR resource) {
        msgStLinkUpdaterOnAdd.updateStorageResLinks(resource);
    }

    protected void updateEventStorageLinksAfterDelete(CR resource) {
        eventStLinkUpdaterOnDelete.updateStorageResLinks(resource);
    }

    protected void updateMsgStorageLinksAfterDelete(CR resource) {
        msgStLinkUpdaterOnDelete.updateStorageResLinks(resource);
    }

    private void updateGrpcLinkedResourcesIfNeeded(CR currentResource) {

        var currentResName = extractName(currentResource);

        var namespace = extractNamespace(currentResource);

        var lSingleton = OperatorState.INSTANCE;

        var grpcLinks = lSingleton.getGrpLinks(namespace);

        Set<String> linkedResources = getGrpcLinkedResources(currentResource, grpcLinks);

        if (!linkedResources.isEmpty()) {
            logger.info("Updating all linked boxes of '{}.{}' resource...", namespace, currentResName);

            for (var linkedResourceName : linkedResources) {
                logger.debug("Checking linked resource: '{}.{}'", namespace, linkedResourceName);

                var hr = OperatorState.INSTANCE.getHelmReleaseFromCache(linkedResourceName, namespace);
                if (hr == null) {
                    logger.info("HelmRelease of '{}.{}' resource not found in cache", namespace, linkedResourceName);
                    continue;
                } else {
                    logger.debug("Found HelmRelease \"{}\"", CustomResourceUtils.annotationFor(hr));
                }

                var hrRawGrpcConfig = extractHelmReleaseRawGrpcConfig(hr);

                HasMetadata linkedResource = OperatorState.INSTANCE.getResourceFromCache(linkedResourceName, namespace);
                var newResGrpcConfig = grpcConfigFactory.createConfig((Th2CustomResource) linkedResource, grpcLinks);

                var newResRawGrpcConfig = JsonUtils.writeValueAsDeepMap(newResGrpcConfig);

                if (!newResRawGrpcConfig.equals(hrRawGrpcConfig)) {
                    logger.info("Updating helm release of '{}.{}' resource", namespace, linkedResourceName);

                    hr.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS, Map.of(
                            GRPC_P2P_CONFIG_ALIAS, newResRawGrpcConfig
                    ));

                    createKubObj(extractNamespace(hr), hr);
                    logger.debug("Updated HelmRelease \"{}\"", CustomResourceUtils.annotationFor(hr));

                } else {
                    logger.info("Resource '{}.{}' doesn't need updating", namespace, linkedResourceName);
                }

            }
        }
    }

    private Map<String, Object> extractHelmReleaseRawGrpcConfig(HelmRelease helmRelease) {
        var values = (Map<String, Object>) helmRelease.getValuesSection();
        var componentConfigs = (Map<String, Object>) values.get(ROOT_PROPERTIES_ALIAS);
        return (Map<String, Object>) componentConfigs.get(GRPC_P2P_CONFIG_ALIAS);
    }

    private Set<String> getGrpcLinkedResources(Th2CustomResource resource, List<PinCouplingGRPC> grpcLinks) {
        Set<String> resources = new HashSet<>();

        for (var grpcLink : grpcLinks) {
            if (grpcLink.getTo().getBoxName().equals(extractName(resource))) {
                resources.add(grpcLink.getFrom().getBoxName());
            }
        }
        return resources;
    }

}
