/*
 * Copyright 2024-2024 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.infraoperator.integration

import com.exactpro.th2.infraoperator.metrics.OperatorMetrics.KEY_DETECTION_TIME
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.SECRET_TYPE_OPAQUE
import com.exactpro.th2.infraoperator.spec.Th2CustomResource
import com.exactpro.th2.infraoperator.spec.Th2Spec
import com.exactpro.th2.infraoperator.spec.dictionary.Th2Dictionary
import com.exactpro.th2.infraoperator.spec.dictionary.Th2DictionarySpec
import com.exactpro.th2.infraoperator.spec.shared.status.RolloutPhase
import com.exactpro.th2.infraoperator.util.CustomResourceUtils.GIT_COMMIT_HASH
import com.exactpro.th2.infraoperator.util.ExtractUtils.KEY_SOURCE_HASH
import com.exactpro.th2.infraoperator.util.JsonUtils.YAML_MAPPER
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.Deletable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private val K_LOGGER = KotlinLogging.logger {}

fun KubernetesClient.createNamespace(
    namespace: String,
    timeout: Long = 200,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    resource(
        Namespace().apply {
            metadata = createMeta(namespace, null)
        },
    ).create()

    await("createNamespace('$namespace')")
        .timeout(timeout, unit)
        .until { namespaces().withName(namespace) != null }
}

fun KubernetesClient.deleteNamespace(
    namespace: String,
    timeout: Long = 200,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    namespaces()
        .withName(namespace)
        ?.awaitDeleteResource("deleteNamespace('$namespace')", timeout, unit)
}

fun KubernetesClient.createSecret(
    namespace: String,
    name: String,
    annotations: Map<String, String>,
    data: Map<String, String>,
) {
    resource(
        Secret().apply {
            metadata = createMeta(name, namespace, annotations)
            this.type = SECRET_TYPE_OPAQUE
            this.data =
                data.mapValues { (_, value) ->
                    String(Base64.getEncoder().encode(value.toByteArray()))
                }
        },
    ).create()
}

fun KubernetesClient.createConfigMap(
    namespace: String,
    name: String,
    annotations: Map<String, String>,
    data: Map<String, String>,
) {
    resource(
        ConfigMap().apply {
            metadata = createMeta(name, namespace, annotations)
            this.data.putAll(data)
        },
    ).create()
}

inline fun <reified T: HasMetadata> KubernetesClient.awaitResource(
    namespace: String,
    name: String,
    timeout: Long = 5_000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
): T {
    await("awaitResource ($name ${T::class.java})")
        .timeout(timeout, unit)
        .until { resources(T::class.java).inNamespace(namespace).withName(name).get() != null }

    return resources(T::class.java).inNamespace(namespace).withName(name).get()
}

inline fun <reified T: HasMetadata> KubernetesClient.awaitNoResources(
    namespace: String,
    timeout: Long = 5_000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    await("awaitNoConfigMaps (${T::class.java})")
        .timeout(timeout, unit)
        .until { resources(T::class.java).inNamespace(namespace).list().items.isEmpty() }
}

inline fun <reified T: HasMetadata> KubernetesClient.awaitNoResource(
    namespace: String,
    name: String,
    timeout: Long = 5_000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    await("awaitNoResource ($name ${T::class.java})")
        .timeout(timeout, unit)
        .until { resources(T::class.java).inNamespace(namespace).withName(name).get() == null }
}

inline fun <reified T: Th2CustomResource> KubernetesClient.awaitPhase(
    namespace: String,
    name: String,
    phase: RolloutPhase,
    timeout: Long = 5_000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) = awaitPhase(namespace, name, phase, T::class.java, timeout, unit)

fun KubernetesClient.awaitPhase(
    namespace: String,
    name: String,
    phase: RolloutPhase,
    resourceType: Class<out Th2CustomResource>,
    timeout: Long = 5_000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    await("awaitStatus ($name $resourceType $phase)")
        .timeout(timeout, unit)
        .until {  resources(resourceType)?.inNamespace(namespace)?.withName(name)?.get()?.status?.phase == phase }
}

fun <T: Th2CustomResource> KubernetesClient.createTh2CustomResource(
    namespace: String,
    name: String,
    gitHash: String,
    spec: String,
    create: () -> T,
): T = create().apply {
    this.metadata = createMeta(name, namespace, createAnnotations(gitHash, spec.hashCode().toString()))
    this.spec = YAML_MAPPER.readValue(spec, Th2Spec::class.java)
}.also {
    resource(it).create()
}

fun <T: Th2CustomResource> KubernetesClient.modifyTh2CustomResource(
    namespace: String,
    name: String,
    gitHash: String,
    spec: String,
    resourceType: Class<T>,
): T = resources(resourceType).inNamespace(namespace).withName(name).get().apply {
    this.metadata.annotations.putAll(createAnnotations(gitHash, spec.hashCode().toString()))
    this.metadata.generation += 1
    this.spec = YAML_MAPPER.readValue(spec, Th2Spec::class.java)
}.also {
    resource(it).update()
}

fun KubernetesClient.createTh2Dictionary(
    namespace: String,
    name: String,
    annotations: Map<String, String>,
    spec: String,
) {
    resource(
        Th2Dictionary().apply {
        this.metadata = createMeta(name, namespace, annotations)
        this.spec = YAML_MAPPER.readValue(spec, Th2DictionarySpec::class.java)
    }
    ).create()
}

fun createAnnotations(
    gitHash: String,
    sourceHash: Any,
) = mapOf(
    KEY_DETECTION_TIME to System.currentTimeMillis().toString(),
    GIT_COMMIT_HASH to gitHash,
    KEY_SOURCE_HASH to sourceHash.hashCode().toString(),
)

private fun Deletable.awaitDeleteResource(
    alias: String,
    timeout: Long,
    unit: TimeUnit,
) {
    delete().also {
        K_LOGGER.info { "Deleted ($alias)" }
        if (it.isEmpty()) return
    }

    val counter = AtomicInteger(0)
    await(alias)
        .timeout(timeout, unit)
        .until { delete().also { counter.incrementAndGet() }.isEmpty() }
    K_LOGGER.info { "Deleted ($alias) after $counter iterations" }
}

private fun createMeta(
    name: String,
    namespace: String?,
    annotations: Map<String, String> = emptyMap(),
): ObjectMeta = ObjectMeta().apply {
    this.name = name
    this.namespace = namespace
    this.annotations.putAll(annotations)
    this.uid = UUID.randomUUID().toString()
    this.creationTimestamp = Instant.now().toString()
}
