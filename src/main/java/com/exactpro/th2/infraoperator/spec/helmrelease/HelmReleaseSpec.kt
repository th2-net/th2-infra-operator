/*
 * Copyright 2020-2022 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.infraoperator.spec.helmrelease

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.fabric8.kubernetes.api.model.KubernetesResource

@JsonDeserialize
class HelmReleaseSpec : KubernetesResource {
    val maxHistory: Int = 2

    // TODO remove resetValues and wait when switched to helm controller
    val resetValues: Boolean = true

    val wait: Boolean = false

    var releaseName: String? = null

    // TODO change type to ChartConfig
    var chart: Any? = null

    val values: Map<String, Any> = HashMap<String, Any>().apply {
        put(HelmRelease.ROOT_PROPERTIES_ALIAS, HashMap<String, Any>())
    }
// TODO restore this fields when switched to helm controller
//    var timeout: String = "2m"
//    val interval: String? = null
//    val install: Install = Install()
//    val uninstall: Uninstall = Uninstall()
//    val upgrade: Upgrade = Upgrade()
}

data class Install(
    val createNamespace: Boolean = false,
    val disableHooks: Boolean = true,
    val disableOpenAPIValidation: Boolean = true,
    val disableWait: Boolean = true,
    val disableWaitForJobs: Boolean = true
)

data class Uninstall(
    val disableHooks: Boolean = true,
    val disableWait: Boolean = true,
    val keepHistory: Boolean = true
)

data class Upgrade(
    val disableHooks: Boolean = true,
    val disableOpenAPIValidation: Boolean = true,
    val disableWait: Boolean = true,
    val disableWaitForJobs: Boolean = true,
    val force: Boolean = true,
    val preserveValues: Boolean = false
)
