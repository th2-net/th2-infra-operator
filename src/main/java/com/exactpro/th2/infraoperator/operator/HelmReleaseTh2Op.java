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
import com.exactpro.th2.infraoperator.model.box.dictionary.DictionaryEntity;
import com.exactpro.th2.infraoperator.model.box.grpc.GrpcRouterConfiguration;
import com.exactpro.th2.infraoperator.model.box.grpc.factory.GrpcRouterConfigFactory;
import com.exactpro.th2.infraoperator.model.box.mq.MessageRouterConfiguration;
import com.exactpro.th2.infraoperator.model.box.mq.factory.MessageRouterConfigFactory;
import com.exactpro.th2.infraoperator.operator.context.HelmOperatorContext;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.Th2Spec;
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease;
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmReleaseSecrets;
import com.exactpro.th2.infraoperator.spec.shared.PrometheusConfiguration;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.BindQueueLinkResolver;
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

    public static final String DICTIONARIES_ALIAS = "dictionaries";

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

    protected final MessageRouterConfigFactory mqConfigFactory;

    protected final GrpcRouterConfigFactory grpcConfigFactory;

    protected final MixedOperation<HelmRelease, KubernetesResourceList<HelmRelease>, Resource<HelmRelease>>
            helmReleaseClient;

    public HelmReleaseTh2Op(HelmOperatorContext.Builder<?, ?> builder) {

        super(builder.getClient());

        this.mqConfigFactory = builder.getMqConfigFactory();
        this.grpcConfigFactory = builder.getGrpcConfigFactory();

        helmReleaseClient = kubClient.resources(HelmRelease.class);
    }

    public abstract SharedInformer<CR> generateInformerFromFactory(SharedInformerFactory factory);

    @Override
    protected void mapProperties(CR resource, HelmRelease helmRelease) {
        super.mapProperties(resource, helmRelease);

        Th2Spec resSpec = resource.getSpec();
        helmRelease.putSpecProp(RELEASE_NAME_ALIAS, extractNamespace(helmRelease) + "-" + extractName(helmRelease));

        mapRouterConfigs(resource, helmRelease);
        mapBoxConfigurations(resource, helmRelease);
        mapBookConfigurations(resource, helmRelease);
        mapCustomSecrets(resource, helmRelease);
        mapDictionaries(resource, helmRelease);
        mapPrometheus(resource, helmRelease);
        mapExtendedSettings(resource, helmRelease);
        mapIngress(helmRelease);
        mapChartConfig(resource, helmRelease);
        mapAnnotations(resource, helmRelease);

        helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS, Map.of(
                DOCKER_IMAGE_ALIAS, resSpec.getImageName() + ":" + resSpec.getImageVersion(),
                COMPONENT_NAME_ALIAS, resource.getMetadata().getName(),
                SCHEMA_SECRETS_ALIAS, new HelmReleaseSecrets(OperatorConfig.INSTANCE.getSchemaSecrets()),
                PULL_SECRETS_ALIAS, OperatorConfig.INSTANCE.getImagePullSecrets(),
                CUSTOM_CONFIG_ALIAS, resource.getSpec().getCustomConfig()
        ));
    }

    private void mapRouterConfigs(CR resource, HelmRelease helmRelease) {
        MessageRouterConfiguration mqConfig = mqConfigFactory.createConfig(resource);
        GrpcRouterConfiguration grpcConfig = grpcConfigFactory.createConfig(resource);
        helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS, Map.of(
                MQ_QUEUE_CONFIG_ALIAS, JsonUtils.writeValueAsDeepMap(mqConfig),
                GRPC_P2P_CONFIG_ALIAS, JsonUtils.writeValueAsDeepMap(grpcConfig)
        ));
    }

    private void mapBoxConfigurations(CR resource, HelmRelease helmRelease) {
        OperatorState operatorState = OperatorState.INSTANCE;
        String resNamespace = extractNamespace(resource);
        var resSpec = resource.getSpec();

        String loggingConfigChecksum = operatorState.getConfigChecksum(resNamespace, LOGGING_ALIAS);
        String mqRouterChecksum = operatorState.getConfigChecksum(resNamespace, MQ_ROUTER_ALIAS);
        String grpcRouterChecksum = operatorState.getConfigChecksum(resNamespace, GRPC_ROUTER_ALIAS);
        String cradleManagerChecksum = operatorState.getConfigChecksum(resNamespace, CRADLE_MGR_ALIAS);

        Map<String, Object> logFileSection = new HashMap<>();
        Map<String, Object> mqRouterSection = new HashMap<>();
        Map<String, Object> grpcRouterSection = new HashMap<>();
        Map<String, Object> cradleManagerSection = new HashMap<>();


        String logFile = resSpec.getLoggingConfig();
        logFileSection.put(CONFIG_ALIAS, logFile);
        logFileSection.put(CHECKSUM_ALIAS, loggingConfigChecksum);


        try {
            Map<String, Object> mqRouterConfig = resSpec.getMqRouter();
            if (mqRouterConfig != null) {
                mqRouterSection.put(CONFIG_ALIAS,
                        mergeConfigs(operatorState.getConfigData(resNamespace, MQ_ROUTER_ALIAS), mqRouterConfig));
            } else {
                mqRouterSection.put(CONFIG_ALIAS, null);
            }
            mqRouterSection.put(CHECKSUM_ALIAS, mqRouterChecksum);

            Map<String, Object> grpcRouterConfig = resSpec.getGrpcRouter();
            if (grpcRouterConfig != null) {
                grpcRouterSection.put(CONFIG_ALIAS,
                        mergeConfigs(operatorState.getConfigData(resNamespace, GRPC_ROUTER_ALIAS), grpcRouterConfig));
            } else {
                grpcRouterSection.put(CONFIG_ALIAS, null);
            }
            grpcRouterSection.put(CHECKSUM_ALIAS, grpcRouterChecksum);

            Map<String, Object> cradleManagerConfig = resSpec.getCradleManager();
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

        helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS, Map.of(
                MQ_ROUTER_ALIAS, mqRouterSection,
                GRPC_ROUTER_ALIAS, grpcRouterSection,
                CRADLE_MGR_ALIAS, cradleManagerSection,
                LOGGING_ALIAS, logFileSection
        ));
    }

    private void mapBookConfigurations(CR resource, HelmRelease helmRelease) {
        OperatorState operatorState = OperatorState.INSTANCE;

        String defaultBookName = operatorState.getBookName(resource.getMetadata().getNamespace());
        String crBookName = resource.getSpec().getBookName();
        Map<String, String> bookConfigSection = new HashMap<>();
        bookConfigSection.put(BOOK_NAME_ALIAS, crBookName != null ? crBookName : defaultBookName);

        helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS, Map.of(
                BOOK_CONFIG_ALIAS, bookConfigSection
        ));
    }

    private void mapCustomSecrets(CR resource, HelmRelease helmRelease) {
        Map<String, String> secretValuesConfig = new HashMap<>();
        Map<String, String> secretPathsConfig = new HashMap<>();
        generateSecretsConfig(resource.getSpec().getCustomConfig(), secretValuesConfig, secretPathsConfig);

        helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS, Map.of(
                SECRET_VALUES_CONFIG_ALIAS, secretValuesConfig,
                SECRET_PATHS_CONFIG_ALIAS, secretPathsConfig
        ));
    }

    private void mapDictionaries(CR resource, HelmRelease helmRelease) {
        OperatorState operatorState = OperatorState.INSTANCE;
        String resName = resource.getMetadata().getName();
        String resNamespace = resource.getMetadata().getNamespace();
        Set<DictionaryEntity> dictionariesConfig = new HashSet<>();
        generateDictionariesConfig(resource.getSpec().getCustomConfig(), dictionariesConfig);
        helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS, Map.of(
                DICTIONARIES_ALIAS, dictionariesConfig
        ));
        dictionariesConfig.forEach(
                dictionary -> operatorState.linkResourceToDictionary(resNamespace, dictionary.getName(), resName)
        );
    }

    private void mapPrometheus(CR resource, HelmRelease helmRelease) {
        PrometheusConfiguration<String> prometheusConfig = resource.getSpec().getPrometheus();
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
    }

    private void mapExtendedSettings(CR resource, HelmRelease helmRelease) {
        Map<String, Object> extendedSettings = resource.getSpec().getExtendedSettings();
        if (extendedSettings != null) {
            helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                    Map.of(EXTENDED_SETTINGS_ALIAS, extendedSettings));
        }

    }

    private void mapIngress(HelmRelease helmRelease) {
        Object ingress = OperatorConfig.INSTANCE.getIngress();
        if (ingress != null) {
            helmRelease.mergeValue(PROPERTIES_MERGE_DEPTH, ROOT_PROPERTIES_ALIAS,
                    Map.of(INGRESS_ALIAS, ingress));
        }
    }

    private void mapChartConfig(CR resource, HelmRelease helmRelease) {
        var defaultChartConfig = OperatorConfig.INSTANCE.getComponentChartConfig();
        var chartConfig = resource.getSpec().getChartConfig();
        if (chartConfig != null) {
            defaultChartConfig = defaultChartConfig.overrideWith(chartConfig);
        }
        helmRelease.mergeSpecProp(CHART_PROPERTIES_ALIAS, defaultChartConfig.toMap());
    }

    private void mapAnnotations(CR resource, HelmRelease helmRelease) {
        var annotations = OperatorConfig.INSTANCE.getCommonAnnotations();
        annotations.putAll(extractNeededAnnotations(resource, ANTECEDENT_LABEL_KEY_ALIAS, COMMIT_HASH_LABEL_KEY_ALIAS));
        helmRelease.mergeValue(Map.of(
                ANNOTATIONS_ALIAS, annotations,
                OPENSHIFT_ALIAS, OperatorConfig.INSTANCE.getOpenshift()
        ));
    }

    @Override
    protected void addedEvent(CR resource) throws IOException {

        String namespace = extractNamespace(resource);
        var lock = OperatorState.INSTANCE.getLock(namespace);
        try {
            lock.lock();
            DeclareQueueResolver.resolveAdd(resource);
            BindQueueLinkResolver.resolveDeclaredLinks(resource);
            BindQueueLinkResolver.resolveHiddenLinks(resource);
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

            DeclareQueueResolver.resolveAdd(resource);
            BindQueueLinkResolver.resolveDeclaredLinks(resource);
            BindQueueLinkResolver.resolveHiddenLinks(resource);
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
            DeclareQueueResolver.resolveDelete(resource);
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

    private void updateGrpcLinkedResourcesIfNeeded(CR currentResource) {

        var currentResName = extractName(currentResource);

        var namespace = extractNamespace(currentResource);

        Set<String> linkedResources = getGrpcLinkedResources(currentResource);

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
                var newResGrpcConfig = grpcConfigFactory.createConfig((Th2CustomResource) linkedResource);

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

    private Set<String> getGrpcLinkedResources(Th2CustomResource resource) {
        Set<String> resources = new HashSet<>();

        for (var grClientPin : resource.getSpec().getPins().getGrpc().getClient()) {
            for (var grpcLink : grClientPin.getLinkTo()) {
                resources.add(grpcLink.getBox());
            }
        }
        return resources;
    }

}
