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

import com.exactpro.th2.infraoperator.configuration.OperatorConfig.Companion.DEFAULT_RABBITMQ_CONFIGMAP_NAME
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQConfig
import com.exactpro.th2.infraoperator.configuration.fields.RabbitMQConfig.Companion.CONFIG_MAP_RABBITMQ_PROP_NAME
import com.exactpro.th2.infraoperator.operator.manager.impl.ConfigMapEventHandler.SECRET_TYPE_OPAQUE
import com.exactpro.th2.infraoperator.util.JsonUtils.JSON_MAPPER
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.client.KubernetesClient
import java.util.Base64

fun KubernetesClient.createNamespace(
    namespace: String,
) {
    resource(Namespace().apply {
        metadata = ObjectMeta().apply {
            name = namespace
        }
    }).create()
}

fun KubernetesClient.createSecret(
    name: String,
    namespace: String,
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

fun KubernetesClient.createRabbitMQAppConfigCfgMap(
    namespace: String,
    data: RabbitMQConfig,
) {
    resource(ConfigMap().apply {
        metadata = ObjectMeta().apply {
            this.name = DEFAULT_RABBITMQ_CONFIGMAP_NAME
            this.namespace = namespace
        }
        this.data[CONFIG_MAP_RABBITMQ_PROP_NAME] = JSON_MAPPER.writeValueAsString(data)
    }).create()
}