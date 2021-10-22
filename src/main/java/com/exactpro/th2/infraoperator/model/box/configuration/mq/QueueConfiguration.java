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

package com.exactpro.th2.infraoperator.model.box.configuration.mq;

import com.exactpro.th2.infraoperator.model.box.schema.link.QueueDescription;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.QueueName;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.RoutingKeyName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Objects;
import java.util.Set;

@JsonDeserialize(builder = QueueConfiguration.Builder.class)
public final class QueueConfiguration {

    private QueueDescription queue;

    private Set<String> attributes;

    //TODO change from Object to RouterFilterConfiguration
    private Set<Object> filters;

    public QueueConfiguration(QueueDescription queue, Set<String> attributes, Set<Object> filters) {
        this.queue = queue;
        this.attributes = attributes;
        this.filters = filters;
    }

    @JsonProperty("queue")
    public String getQueueName() {
        return this.queue.getQueueName().toString();
    }

    @JsonProperty("name")
    public String getRouterKeyName() {
        return this.queue.getRoutingKey().toString();
    }

    @JsonProperty("exchange")
    public String getExchange() {
        return this.queue.getExchange();
    }

    @JsonProperty("attributes")
    public Set<String> getAttributes() {
        return this.attributes;
    }

    @JsonProperty("filters")
    public Set<Object> getFilters() {
        return this.filters;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof QueueConfiguration)) {
            return false;
        }

        final QueueConfiguration other = (QueueConfiguration) o;
        return Objects.equals(this.attributes, other.attributes) &&
                Objects.equals(this.filters, other.filters) &&
                Objects.equals(this.queue, other.queue);
    }

    public Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String queueName;

        private String routingkey;

        private String exchange;

        private Set<String> attributes;

        private Set<Object> filters;

        Builder() { }

        @JsonProperty("queue")
        public Builder queueName(String queueName) {
            this.queueName = queueName;
            return this;
        }

        @JsonProperty("name")
        public Builder routingKey(String routingkey) {
            this.routingkey = routingkey;
            return this;
        }

        @JsonProperty("exchange")
        public Builder exchange(String exchange) {
            this.exchange = exchange;
            return this;
        }

        @JsonProperty("attributes")
        public Builder attributes(Set<String> attributes) {
            this.attributes = attributes;
            return this;
        }

        @JsonProperty("filters")
        public Builder filters(Set<Object> filters) {
            this.filters = filters;
            return this;
        }

        public QueueConfiguration build() {
            return new QueueConfiguration(
                    new QueueDescription(
                            QueueName.fromString(queueName),
                            RoutingKeyName.fromString(routingkey),
                            exchange
                    ),
                    attributes, filters);
        }
    }
}
