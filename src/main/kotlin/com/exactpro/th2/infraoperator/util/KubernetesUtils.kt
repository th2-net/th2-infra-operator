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

@file:JvmName("KubernetesUtils")

package com.exactpro.th2.infraoperator.util

import com.exactpro.th2.infraoperator.spec.Th2CustomResource
import com.exactpro.th2.infraoperator.spec.box.Th2Box
import com.exactpro.th2.infraoperator.spec.corebox.Th2CoreBox
import com.exactpro.th2.infraoperator.spec.estore.Th2Estore
import com.exactpro.th2.infraoperator.spec.job.Th2Job
import com.exactpro.th2.infraoperator.spec.mstore.Th2Mstore
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import mu.KotlinLogging
import kotlin.streams.toList

private val K_LOGGER = KotlinLogging.logger { }

val CUSTOM_RESOURCE_KINDS =
    setOf(Th2Estore::class.java, Th2Mstore::class.java, Th2CoreBox::class.java, Th2Box::class.java, Th2Job::class.java)

fun createKubernetesClient(): KubernetesClient = KubernetesClientBuilder().build()

fun KubernetesClient.namespaces(namespacePrefixes: Set<String>): Set<String> =
    namespaces()
        .list()
        .items
        .map { it.metadata.name }
        .filter { ns -> Strings.anyPrefixMatch(ns, namespacePrefixes) }
        .toSet()

fun KubernetesClient.customResources(namespace: String): List<Th2CustomResource> =
    CUSTOM_RESOURCE_KINDS
        .stream()
        .flatMap {
            resources(it)
                .inNamespace(namespace)
                .resources()
        }.map { it.get() as Th2CustomResource }
        .toList()

fun KubernetesClient.isNotActive(namespace: String): Boolean {
    val namespaceObj: Namespace? = namespaces().withName(namespace).get()
    if (namespaceObj == null || namespaceObj.status.phase != "Active") {
        K_LOGGER.info { "Namespace \"$namespace\" deleted or not active, cancelling" }
        return true
    }
    return false
}
