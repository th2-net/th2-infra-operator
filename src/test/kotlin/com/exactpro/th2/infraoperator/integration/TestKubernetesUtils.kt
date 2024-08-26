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
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.Deletable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.util.Base64
import java.util.concurrent.TimeUnit

private val K_LOGGER = KotlinLogging.logger {}

fun KubernetesClient.createNamespace(
    namespace: String,
    timeout: Long = 200,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    resource(Namespace().apply {
        metadata = ObjectMeta().apply {
            name = namespace
        }
    }).create()

    await("createNamespace('$namespace')")
        .timeout(timeout, unit)
        .until { namespaces().withName(namespace) != null }
}

fun KubernetesClient.deleteNamespace(
    namespace: String,
    timeout: Long = 200,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    namespaces().withName(namespace)
        ?.awaitDeleteResource("deleteNamespace('$namespace')", timeout, unit)
}

fun KubernetesClient.createSecret(
    namespace: String,
    name: String,
    data: Map<String, String>,
) {
    resource(Secret().apply {
        metadata = ObjectMeta().apply {
            this.name = name
            this.namespace = namespace
        }
        this.type = SECRET_TYPE_OPAQUE
        this.data = data.mapValues { (_, value) -> String(Base64.getEncoder().encode(value.toByteArray())); }
    }).create()
}

fun KubernetesClient.deleteSecret(
    namespace: String,
    name: String,
    timeout: Long = 200,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    secrets().inNamespace(namespace)?.withName(name)
        ?.awaitDeleteResource("deleteSecret('$namespace/$name')", timeout, unit)
}

fun KubernetesClient.createConfigMap(
    namespace: String,
    name: String,
    data: Map<String, String>,
) {
    resource(ConfigMap().apply {
        metadata = ObjectMeta().apply {
            this.name = name
            this.namespace = namespace
        }
        this.data.putAll(data)
    }).create()
}

fun KubernetesClient.deleteConfigMap(
    namespace: String,
    name: String,
    timeout: Long = 200,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
) {
    configMaps().inNamespace(namespace)?.withName(name)
        ?.awaitDeleteResource("deleteConfigMap('$namespace/$name')", timeout, unit)
}

private fun Deletable.awaitDeleteResource(
    alias: String,
    timeout: Long,
    unit: TimeUnit,
) {
    delete().also {
        K_LOGGER.info { "Delete status ($alias): $it" }
        if (it.isEmpty()) return
    }
    await(alias)
        .timeout(timeout, unit)
        .until { delete().also { K_LOGGER.info { "Delete status ($alias): $it" }}.isEmpty() }
}