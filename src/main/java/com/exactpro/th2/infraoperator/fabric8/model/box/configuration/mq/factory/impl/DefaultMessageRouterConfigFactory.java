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

package com.exactpro.th2.infraoperator.fabric8.model.box.configuration.mq.factory.impl;

import com.exactpro.th2.infraoperator.fabric8.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.mq.MessageRouterConfiguration;
import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.mq.QueueConfiguration;
import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.mq.RouterFilterConfiguration;
import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.mq.factory.MessageRouterConfigFactory;
import com.exactpro.th2.infraoperator.fabric8.model.box.schema.link.QueueBunch;
import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.configmaps.ConfigMaps;
import com.exactpro.th2.infraoperator.fabric8.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.boxes.box.impl.BoxMq;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.DirectionAttribute;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.FilterSpec;
import com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils;
import com.exactpro.th2.infraoperator.fabric8.util.SchemeMappingUtils;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


public class DefaultMessageRouterConfigFactory implements MessageRouterConfigFactory {

    private static final String NOT_NECESSARY_STUB = "not_necessary";


    @Override
    public MessageRouterConfiguration createConfig(Th2CustomResource resource) {
        return mqPinsToMsgConfig(resource);
    }


    private MessageRouterConfiguration mqPinsToMsgConfig(Th2CustomResource resource) {

        Map<String, QueueConfiguration> queues = new HashMap<>();

        for (var pin : ExtractUtils.extractMqPins(resource)) {

            var attrs = pin.getAttributes();

            String boxName = ExtractUtils.extractName(resource);

            BoxMq boxMq = BoxMq.builder()
                    .box(boxName)
                    .pin(pin.getName())
                    .build();

            QueueBunch queueSpec;

            if (attrs.contains(DirectionAttribute.publish.name())) {
                queueSpec = createQueueBunch(ExtractUtils.extractNamespace(resource), NOT_NECESSARY_STUB, boxMq.toString());
            } else {
                queueSpec = createQueueBunch(ExtractUtils.extractNamespace(resource), boxMq.toString(), NOT_NECESSARY_STUB);
            }

            //TODO null check
            var queueConfig = QueueConfiguration.builder()
                    .exchange(queueSpec.getExchange())
                    .attributes(pin.getAttributes())
                    .filters(specToConfigFilters(pin.getFilters()))
                    .name(queueSpec.getRoutingKey())
                    .queue(queueSpec.getQueue())
                    .build();

            queues.put(pin.getName(), queueConfig);
        }

        return MessageRouterConfiguration.builder()
                .queues(queues)
                .build();
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
    private QueueBunch createQueueBunch(String namespace, String queue, String routingKey) {

        var rabbitMQConfig = ConfigMaps.INSTANCE.getRabbitMQConfig4Namespace(namespace);

        if (Objects.isNull(rabbitMQConfig)) {
            return null;
        }

        String fullQueue = queue.equals(NOT_NECESSARY_STUB) ? queue : OperatorConfig.QUEUE_PREFIX + namespace + "_" + queue;

        String fullRoutingKey = routingKey.equals(NOT_NECESSARY_STUB) ? routingKey : OperatorConfig.ROUTING_KEY_PREFIX + namespace + "_" + routingKey;

        return new QueueBunch(
                fullQueue,
                fullRoutingKey,
                rabbitMQConfig.getExchangeName()
        );
    }

}
