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

package com.exactpro.th2.infraoperator.configuration

import com.exactpro.th2.infraoperator.configuration.fields.ChartConfig
import com.exactpro.th2.infraoperator.configuration.fields.EnvironmentConfig
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQManagementConfig
import com.exactpro.th2.infraoperator.configuration.fields.SchemaSecrets
import com.exactpro.th2.infraoperator.spec.shared.PrometheusConfiguration

data class OperatorConfig(
    var chart: ChartConfig = ChartConfig(),
    var rabbitMQManagement: RabbitMQManagementConfig = RabbitMQManagementConfig(),
    var schemaSecrets: SchemaSecrets = SchemaSecrets(),
    var namespacePrefixes: List<String> = ArrayList(),
    var rabbitMQConfigMapName: String = DEFAULT_RABBITMQ_CONFIGMAP_NAME,
    var k8sUrl: String = "",
    var prometheusConfiguration: PrometheusConfiguration<String> = PrometheusConfiguration.createDefault("true"),
    var commonAnnotations: Map<String, String> = HashMap(),
    var ingress: Any? = null,
    var imagePullSecrets: List<String> = ArrayList(),
    var openshift: EnvironmentConfig = EnvironmentConfig()
) {
    companion object {
        const val DEFAULT_RABBITMQ_CONFIGMAP_NAME = "rabbit-mq-app-config"
        const val RABBITMQ_SECRET_PASSWORD_KEY = "rabbitmq-password"
    }
}
