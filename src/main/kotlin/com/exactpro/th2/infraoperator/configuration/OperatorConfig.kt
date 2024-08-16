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

package com.exactpro.th2.infraoperator.configuration

import com.exactpro.th2.infraoperator.configuration.fields.EnvironmentConfig
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQManagementConfig
import com.exactpro.th2.infraoperator.configuration.fields.SchemaSecrets
import com.exactpro.th2.infraoperator.spec.shared.PrometheusConfiguration

data class OperatorConfig(
    // TODO change type to ChartConfig with default value ChartConfig()
    var chart: Any? = null,
    var rabbitMQManagement: RabbitMQManagementConfig = RabbitMQManagementConfig(),
    var schemaSecrets: SchemaSecrets = SchemaSecrets(),
    var namespacePrefixes: Set<String> = emptySet(),
    var rabbitMQConfigMapName: String = DEFAULT_RABBITMQ_CONFIGMAP_NAME,
    var k8sUrl: String = "",
    var prometheusConfiguration: PrometheusConfiguration<String> = PrometheusConfiguration.createDefault("true"),
    var commonAnnotations: Map<String, String>? = HashMap(),
    var ingress: Any? = null,
    private var imagePullSecrets: List<String>? = ArrayList(),
    var openshift: EnvironmentConfig = EnvironmentConfig(),
    var rootless: EnvironmentConfig = EnvironmentConfig(),
    var releaseTimeout: String = "2m"
) {
    val imgPullSecrets = imagePullSecrets ?: ArrayList()
    companion object {
        const val DEFAULT_RABBITMQ_CONFIGMAP_NAME = "rabbit-mq-app-config"
        const val RABBITMQ_SECRET_PASSWORD_KEY = "rabbitmq-password"
    }
}
