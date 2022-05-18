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

package com.exactpro.th2.infraoperator.model.box.configuration.mq

import com.exactpro.th2.infraoperator.model.box.schema.link.QueueDescription
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

@JsonDeserialize
data class QueueConfiguration(
    val queue: QueueDescription,
    val attributes: Set<String>,
    val filters: List<Any>
) {
    @get:JsonProperty("queue")
    val queueName: String
        get() = queue.queueName.toString()

    @get:JsonProperty("name")
    val routerKeyName: String
        get() = queue.routingKey.toString()

    @get:JsonProperty("exchange")
    val exchange: String
        get() = queue.exchange
}
