/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.infraoperator.model.box.configuration.mq.factory.impl;

import com.exactpro.th2.infraoperator.model.box.configuration.mq.MessageRouterConfiguration;
import com.exactpro.th2.infraoperator.model.box.configuration.mq.QueueConfiguration;
import com.exactpro.th2.infraoperator.model.box.configuration.mq.RouterFilterConfiguration;
import com.exactpro.th2.infraoperator.model.box.configuration.mq.factory.MessageRouterConfigFactory;
import com.exactpro.th2.infraoperator.model.box.schema.link.QueueDescription;
import com.exactpro.th2.infraoperator.model.kubernetes.configmaps.ConfigMaps;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinMQ;
import com.exactpro.th2.infraoperator.spec.shared.PinAttribute;
import com.exactpro.th2.infraoperator.spec.shared.FilterSpec;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.queue.QueueName;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.queue.RoutingKeyName;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import com.exactpro.th2.infraoperator.util.SchemeMappingUtils;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


public class DefaultMessageRouterConfigFactory implements MessageRouterConfigFactory {

    @Override
    public MessageRouterConfiguration createConfig(Th2CustomResource resource) {

        Map<String, QueueConfiguration> queues = new HashMap<>();

        for (var pin : ExtractUtils.extractMqPins(resource)) {

            var attrs = pin.getAttributes();
            String boxName = ExtractUtils.extractName(resource);
            PinMQ mqPin = new PinMQ(boxName, pin.getName());
            QueueDescription queueSpec;

            if (attrs.contains(PinAttribute.publish.name())) {
                queueSpec = createQueueBunch(ExtractUtils.extractNamespace(resource), null, mqPin);
            } else {
                queueSpec = createQueueBunch(ExtractUtils.extractNamespace(resource), mqPin, null);
            }

            //TODO null check
            queues.put(pin.getName(),
                    new QueueConfiguration(queueSpec, pin.getAttributes(), specToConfigFilters(pin.getFilters())));
        }

        return MessageRouterConfiguration.builder().queues(queues).build();
    }


    private Set<RouterFilterConfiguration> specToConfigFilters(Set<FilterSpec> filterSpecs) {
        return filterSpecs.stream()
                .map(filterSpec ->
                        RouterFilterConfiguration.builder()
                                .metadata(SchemeMappingUtils.specToConfigFieldFilters(filterSpec.getMetadataFilter()))
                                .message(SchemeMappingUtils.specToConfigFieldFilters(filterSpec.getMessageFilter()))
                                .build()
                ).collect(Collectors.toSet());
    }


    @Nullable
    private QueueDescription createQueueBunch(String namespace, PinMQ to, PinMQ from) {

        var rabbitMQConfig = ConfigMaps.INSTANCE.getRabbitMQConfig4Namespace(namespace);

        if (rabbitMQConfig == null)
            return null;

        QueueName queue = (to == null) ? QueueName.EMPTY : new QueueName(namespace, to);
        RoutingKeyName routingKey = (from == null) ? RoutingKeyName.EMPTY : new RoutingKeyName(namespace, from);

        return new QueueDescription(
                queue,
                routingKey,
                rabbitMQConfig.getExchangeName()
        );
    }

}
