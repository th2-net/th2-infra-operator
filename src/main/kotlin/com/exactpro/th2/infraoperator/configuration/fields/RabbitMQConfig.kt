/*
 * Copyright 2020-2025 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.infraoperator.configuration.fields

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

@JsonDeserialize
data class RabbitMQConfig(
    val port: Int,
    val managementPort: Int,
    val host: String,
    @get:JsonProperty("vHost")
    val vHost: String,
    val exchangeName: String,
    val username: String,
    var password: String
) {

    companion object {
        const val CONFIG_MAP_RABBITMQ_PROP_NAME = "rabbitMQ.json"
    }
}
