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

@file:JvmName("WatcherUtils")

package com.exactpro.th2.infraoperator.util

import io.fabric8.kubernetes.client.WatcherException
import io.fabric8.kubernetes.client.informers.ExceptionHandler
import io.github.oshai.kotlinlogging.KotlinLogging

private val K_LOGGER = KotlinLogging.logger { }

fun createExceptionHandler(clazz: Class<*>): ExceptionHandler {
    return ExceptionHandler { isStarted: Boolean, t: Throwable ->
        K_LOGGER.error(t) { "${clazz.simpleName} informer catch error, isStarted: $isStarted" }
        // Default condition copied from io.fabric8.kubernetes.client.informers.impl.cache.Reflector.handler.
        // We should monitor caught errors in real cluster
        //  after that change the condition to maintain a component in working order
        isStarted && t !is WatcherException
    }
}
