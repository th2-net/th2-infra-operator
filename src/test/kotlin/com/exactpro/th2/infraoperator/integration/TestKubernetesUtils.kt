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

import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.SECRET_TYPE_OPAQUE
import com.exactpro.th2.infraoperator.spec.Th2Spec
import com.exactpro.th2.infraoperator.spec.box.Th2Box
import com.exactpro.th2.infraoperator.spec.corebox.Th2CoreBox
import com.exactpro.th2.infraoperator.spec.dictionary.Th2Dictionary
import com.exactpro.th2.infraoperator.spec.dictionary.Th2DictionarySpec
import com.exactpro.th2.infraoperator.spec.estore.Th2Estore
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease
import com.exactpro.th2.infraoperator.spec.mstore.Th2Mstore
import com.exactpro.th2.infraoperator.util.JsonUtils.YAML_MAPPER
import io.fabric8.kubernetes.api.model.ConfigMap
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

fun KubernetesClient.deleteSecret(
    namespace: String,
    name: String,
    timeout: Long = 200,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    secrets()
        .inNamespace(namespace)
        ?.withName(name)
        ?.awaitDeleteResource("deleteSecret('$namespace/$name')", timeout, unit)
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

fun KubernetesClient.awaitConfigMap(
    namespace: String,
    name: String,
    timeout: Long = 5_000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
): ConfigMap {
    await("awaitConfigMap ($name)")
        .timeout(timeout, unit)
        .until { resources(ConfigMap::class.java).inNamespace(namespace).withName(name).get() != null }

    return resources(ConfigMap::class.java).inNamespace(namespace).withName(name).get()
}

fun KubernetesClient.awaitNoConfigMap(
    namespace: String,
    timeout: Long = 5_000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    await("awaitNoConfigMap")
        .timeout(timeout, unit)
        .until { resources(ConfigMap::class.java).inNamespace(namespace).list().items.isEmpty() }
}

fun KubernetesClient.deleteConfigMap(
    namespace: String,
    name: String,
    timeout: Long = 200,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    configMaps()
        .inNamespace(namespace)
        ?.withName(name)
        ?.awaitDeleteResource("deleteConfigMap('$namespace/$name')", timeout, unit)
}

fun KubernetesClient.createTh2Mstore(
    namespace: String,
    name: String,
    annotations: Map<String, String>,
) {
    resource(
        Th2Mstore().apply {
            metadata = createMeta(name, namespace, annotations)
            spec = YAML_MAPPER.readValue(
                """
                imageName: ghcr.io/th2-net/th2-mstore
                imageVersion: 0.0.0
                """.trimIndent(),
                Th2Spec::class.java
            )
        }
    ).create()
}

fun KubernetesClient.createTh2Estore(
    namespace: String,
    name: String,
    annotations: Map<String, String>,
) {
    resource(
        Th2Estore().apply {
            metadata = createMeta(name, namespace, annotations)
            spec = YAML_MAPPER.readValue(
                """
                imageName: ghcr.io/th2-net/th2-estore
                imageVersion: 0.0.0
                """.trimIndent(),
                Th2Spec::class.java
            )
        }
    ).create()
}

fun KubernetesClient.createTh2CoreBox(
    namespace: String,
    name: String,
    annotations: Map<String, String>,
    spec: String,
) {
    resource(
        Th2CoreBox().apply {
            this.metadata = createMeta(name, namespace, annotations)
            this.spec = YAML_MAPPER.readValue(spec, Th2Spec::class.java)
        }
    ).create()
}

fun KubernetesClient.createTh2Box(
    namespace: String,
    name: String,
    annotations: Map<String, String>,
    spec: String,
) {
    resource(
        Th2Box().apply {
            this.metadata = createMeta(name, namespace, annotations)
            this.spec = YAML_MAPPER.readValue(spec, Th2Spec::class.java)
        }
    ).create()
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

fun KubernetesClient.awaitHelmRelease(
    namespace: String,
    name: String,
    timeout: Long = 5_000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
): HelmRelease {
    await("awaitHelmRelease ($name)")
        .timeout(timeout, unit)
        .until { resources(HelmRelease::class.java).inNamespace(namespace).withName(name).get() != null }

    return resources(HelmRelease::class.java).inNamespace(namespace).withName(name).get()
}

fun KubernetesClient.awaitNoHelmRelease(
    namespace: String,
    timeout: Long = 2000,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    await("awaitNoHelmRelease")
        .timeout(timeout, unit)
        .until { resources(HelmRelease::class.java).inNamespace(namespace).list().items.isEmpty() }
}

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
