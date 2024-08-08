/*
 * Copyright 2024 Exactpro (Exactpro Systems Limited)
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
@file:JvmName("Util")

package com.exactpro.th2.infraoperator.spec.strategy.linkresolver

import com.exactpro.th2.infraoperator.operator.impl.EstoreHelmTh2Op.EVENT_STORAGE_BOX_ALIAS
import com.exactpro.th2.infraoperator.operator.impl.EstoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS
import com.exactpro.th2.infraoperator.operator.impl.MstoreHelmTh2Op.MESSAGE_STORAGE_BOX_ALIAS
import com.exactpro.th2.infraoperator.operator.impl.MstoreHelmTh2Op.MESSAGE_STORAGE_PIN_ALIAS
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.QueueName
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.RoutingKeyName


fun buildEstoreQueueName(namespace: String): QueueName = QueueName(
    namespace,
    EVENT_STORAGE_BOX_ALIAS,
    EVENT_STORAGE_PIN_ALIAS
)

fun buildEstoreQueue(namespace: String): String = buildEstoreQueueName(namespace).toString()

fun buildEstoreQueueName(namespace: String, component: String): QueueName = QueueName(
    namespace,
    component,
    EVENT_STORAGE_PIN_ALIAS
)

fun buildEstoreQueue(namespace: String, component: String): String =
    buildEstoreQueueName(namespace, component).toString()

fun buildEstoreRoutingKeyName(namespace: String, component: String) = RoutingKeyName(namespace, component, EVENT_STORAGE_PIN_ALIAS)

fun buildMstoreQueueName(namespace: String) = QueueName(
    namespace,
    MESSAGE_STORAGE_BOX_ALIAS,
    MESSAGE_STORAGE_PIN_ALIAS
)

fun buildMstoreQueue(namespace: String): String = buildMstoreQueueName(namespace).toString()

fun buildMstoreQueueName(namespace: String, component: String) = QueueName(
    namespace,
    component,
    MESSAGE_STORAGE_PIN_ALIAS
)

fun buildMstoreQueue(namespace: String, component: String): String =
    buildMstoreQueueName(namespace, component).toString()
