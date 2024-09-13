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

package com.exactpro.th2.infraoperator.operator;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.configuration.ConfigLoader;
import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.model.box.dictionary.DictionaryEntity;
import com.exactpro.th2.infraoperator.model.box.grpc.GrpcRouterConfiguration;
import com.exactpro.th2.infraoperator.model.box.grpc.factory.GrpcRouterConfigFactory;
import com.exactpro.th2.infraoperator.model.box.mq.MessageRouterConfiguration;
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
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

import static com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.mergeConfigs;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.*;
import static com.exactpro.th2.infraoperator.util.HelmReleaseUtils.*;

public abstract class HelmReleaseTh2Op<CR extends Th2CustomResource> extends AbstractTh2Operator<CR> {

    private static final Logger logger = LoggerFactory.getLogger(HelmReleaseTh2Op.class);

    public static final String ANTECEDENT_LABEL_KEY_ALIAS = "th2.exactpro.com/antecedent";

    public static final String COMMIT_HASH_LABEL_KEY_ALIAS = "th2.exactpro.com/git-commit-hash";

    //spec section
    private static final String OPENSHIFT_ALIAS = "openshift";

    //values section
    public static final String ANNOTATIONS_ALIAS = "commonAnnotations";

    //component section
    public static final String ROOTLESS_ALIAS = "rootless";

    public static final String COMPONENT_NAME_ALIAS = "name";

    public static final String DOCKER_IMAGE_ALIAS = "image";

    public static final String CUSTOM_CONFIG_ALIAS = "custom";

    public static final String SECRET_VALUES_CONFIG_ALIAS = "secretValuesConfig";

    public static final String SECRET_PATHS_CONFIG_ALIAS = "secretPathsConfig";

    public static final String PROMETHEUS_CONFIG_ALIAS = "prometheus";

    public static final String DICTIONARIES_ALIAS = "dictionaries";

    public static final String MQ_QUEUE_CONFIG_ALIAS = "mq";

    public static final String GRPC_P2P_CONFIG_ALIAS = "grpc";

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

    public static final String IS_JOB_ALIAS = "runAsJob";

    //extended settings section
    public static final String SERVICE_ALIAS = "service";

    public static final String EXTERNAL_BOX_ALIAS = "externalBox";

    //general aliases
    public static final String CONFIG_ALIAS = "config";

    public static final String CHECKSUM_ALIAS = "checksum";

    public static final String ENABLED_ALIAS = "enabled";

    private static final boolean DEFAULT_VALUE_ENABLED = Boolean.TRUE;

    protected final GrpcRouterConfigFactory grpcConfigFactory;

    protected final MixedOperation<HelmRelease, KubernetesResourceList<HelmRelease>, Resource<HelmRelease>>
            helmReleaseClient;

    public HelmReleaseTh2Op(KubernetesClient client) {

        super(client);

        this.grpcConfigFactory = new GrpcRouterConfigFactory();

        helmReleaseClient = kubClient.resources(HelmRelease.class);
    }

    public abstract SharedIndexInformer<CR> generateInformerFromFactory(SharedInformerFactory factory);

    private final OperatorConfig config = ConfigLoader.getConfig();

    @Override
    protected void mapProperties(CR resource, HelmRelease helmRelease) {
        super.mapProperties(resource, helmRelease);

        Th2Spec resSpec = resource.getSpec();
        helmRelease.getSpec().setReleaseName(extractNamespace(helmRelease) + "-" + extractName(helmRelease));

        mapRouterConfigs(resource, helmRelease);
        mapBoxConfigurations(resource, helmRelease);
        mapBookConfigurations(resource, helmRelease);
        mapCustomSecrets(resource, helmRelease);
        mapDictionaries(resource, helmRelease);
        mapPrometheus(resource, helmRelease);
        mapExtendedSettings(resource, helmRelease);
        mapIngress(helmRelease);
        mapRootless(helmRelease);
        mapAnnotations(resource, helmRelease);

        helmRelease.addComponentValues(Map.of(
                DOCKER_IMAGE_ALIAS, resSpec.getImageName() + ":" + resSpec.getImageVersion(),
                COMPONENT_NAME_ALIAS, resource.getMetadata().getName(),
                SCHEMA_SECRETS_ALIAS, new HelmReleaseSecrets(config.getSchemaSecrets()),
                PULL_SECRETS_ALIAS, config.getImgPullSecrets(),
                CUSTOM_CONFIG_ALIAS, resource.getSpec().getCustomConfig(),
                IS_JOB_ALIAS, resource.getSpec().getRunAsJob()
        ));
    }

    private void mapRouterConfigs(CR resource, HelmRelease helmRelease) {
        MessageRouterConfiguration mqConfig = getMqConfigFactory().createConfig(resource);
        GrpcRouterConfiguration grpcConfig = grpcConfigFactory.createConfig(resource);
        helmRelease.addComponentValues(Map.of(
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

        helmRelease.addComponentValues(Map.of(
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

        helmRelease.addComponentValue(BOOK_CONFIG_ALIAS, bookConfigSection);
    }

    private void mapCustomSecrets(CR resource, HelmRelease helmRelease) {
        Map<String, String> secretValuesConfig = new HashMap<>();
        Map<String, String> secretPathsConfig = new HashMap<>();
        generateSecretsConfig(resource.getSpec(), secretValuesConfig, secretPathsConfig);

        helmRelease.addComponentValues(Map.of(
                SECRET_VALUES_CONFIG_ALIAS, secretValuesConfig,
                SECRET_PATHS_CONFIG_ALIAS, secretPathsConfig
        ));
    }

    private void mapDictionaries(CR resource, HelmRelease helmRelease) {
        OperatorState operatorState = OperatorState.INSTANCE;
        String resName = resource.getMetadata().getName();
        String resNamespace = resource.getMetadata().getNamespace();
        Set<DictionaryEntity> dictionariesConfig = new HashSet<>();
        generateDictionariesConfig(resource.getSpec(), dictionariesConfig);
        helmRelease.addComponentValue(DICTIONARIES_ALIAS, dictionariesConfig);
        dictionariesConfig.forEach(
                dictionary -> operatorState.linkResourceToDictionary(resNamespace, dictionary.getName(), resName)
        );
    }

    private void mapPrometheus(CR resource, HelmRelease helmRelease) {
        PrometheusConfiguration<Boolean> prometheusConfig = resource.getSpec().getPrometheus();
        if (prometheusConfig == null) {
            prometheusConfig = PrometheusConfiguration.createDefault(DEFAULT_VALUE_ENABLED);
        }

        PrometheusConfiguration<Boolean> prometheusConfigForRelease = new PrometheusConfiguration<>(
                prometheusConfig.getHost(),
                prometheusConfig.getPort(),
                prometheusConfig.getEnabled()
        );

        helmRelease.addComponentValue(PROMETHEUS_CONFIG_ALIAS, prometheusConfigForRelease);
    }

    private void mapExtendedSettings(CR resource, HelmRelease helmRelease) {
        Map<String, Object> extendedSettings = resource.getSpec().getExtendedSettings();
        if (extendedSettings != null) {
            helmRelease.addComponentValue(EXTENDED_SETTINGS_ALIAS, extendedSettings);
        }

    }

    private void mapIngress(HelmRelease helmRelease) {
        Object ingress = config.getIngress();
        if (ingress != null) {
            helmRelease.addComponentValue(INGRESS_ALIAS, ingress);
        }
    }

    private void mapRootless(HelmRelease helmRelease) {
        helmRelease.addComponentValue(ROOTLESS_ALIAS, config.getRootless());
    }

    private void mapAnnotations(CR resource, HelmRelease helmRelease) {
        var commonAnnotations = config.getCommonAnnotations();
        Map<String, String> annotations;
        if (commonAnnotations == null) {
            annotations = new HashMap<>();
        } else {
            annotations = new HashMap<>(config.getCommonAnnotations());
        }
        annotations.putAll(resource.getMetadata().getAnnotations());
        helmRelease.addValueSection(Map.of(
                ANNOTATIONS_ALIAS, annotations,
                OPENSHIFT_ALIAS, config.getOpenshift()
        ));
    }

    @Override
    protected void addedEvent(CR resource) throws IOException {

        String namespace = extractNamespace(resource);
        var lock = OperatorState.INSTANCE.getLock(namespace);
        lock.lock();
        try {
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
        lock.lock();
        try {
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
        lock.lock();
        try {
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
        helmReleaseClient.inNamespace(namespace).resource(helmRelease).createOrReplace();
        OperatorState.INSTANCE.putHelmReleaseInCache(helmRelease, namespace);
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

                var hrRawGrpcConfig = hr.getComponentValuesSection().get(GRPC_P2P_CONFIG_ALIAS);

                var newResGrpcConfig = grpcConfigFactory.createConfig(
                        OperatorState.INSTANCE.getResourceFromCache(linkedResourceName, namespace)
                );

                var newResRawGrpcConfig = JsonUtils.writeValueAsDeepMap(newResGrpcConfig);

                if (!newResRawGrpcConfig.equals(hrRawGrpcConfig)) {
                    logger.info("Updating helm release of '{}.{}' resource", namespace, linkedResourceName);

                    hr.addComponentValues(Map.of(
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
