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

package com.exactpro.th2.infraoperator.model.kubernetes.configmaps;

import com.exactpro.th2.infraoperator.configuration.RabbitMQConfig;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public enum ConfigMaps {
    INSTANCE;

    private Map<String, RabbitMQConfig> rabbitMQConfigs = new HashMap<>();

    @Nullable
    public synchronized RabbitMQConfig getRabbitMQConfig4Namespace(String namespace) {
        return rabbitMQConfigs.get(namespace);
    }

    public synchronized void setRabbitMQConfig4Namespace(String namespace, RabbitMQConfig config) {
        rabbitMQConfigs.put(namespace, config);
    }
}