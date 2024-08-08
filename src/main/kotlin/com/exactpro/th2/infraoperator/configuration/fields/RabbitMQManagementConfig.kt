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

package com.exactpro.th2.infraoperator.configuration.fields

data class RabbitMQManagementConfig(
    val host: String = "",
    val managementPort: Int = 0,
    val applicationPort: Int = 0,
    val vhostName: String = "",
    val exchangeName: String = "",
    val username: String = "",
    val password: String = "",
    val persistence: Boolean = false,
    val rubbishCollectionInterval: Int = 900,
    val schemaPermissions: RabbitMQNamespacePermissions = RabbitMQNamespacePermissions()
)
