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

package com.exactpro.th2.infraoperator.model.box.schema.link;

import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.QueueName;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.RoutingKeyName;

import java.util.Objects;

public final class QueueDescription {

    private QueueName queueName;
    private RoutingKeyName routingKey;
    private String exchange;


    public QueueDescription(QueueName queueName, RoutingKeyName routingKey, String exchange) {
        this.queueName = queueName;
        this.routingKey = routingKey;
        this.exchange = exchange;
    }


    public QueueName getQueueName() {
        return this.queueName;
    }


    public RoutingKeyName getRoutingKey() {
        return this.routingKey;
    }


    public String getExchange() {
        return this.exchange;
    }


    @Override
    public boolean equals(final Object o) {

        if (o == this)
            return true;
        if (!(o instanceof QueueDescription))
            return false;

        final QueueDescription other = (QueueDescription) o;
        return Objects.equals(this.queueName, other.queueName) &&
                Objects.equals(this.exchange, other.exchange) &&
                Objects.equals(this.routingKey, other.routingKey);
    }


    @Override
    public int hashCode() {
        throw new AssertionError("method not implemented");
    }


    @Override
    public String toString() {
        return String.format("%s[%s:%s:%s]", this.getClass().getName(), queueName, routingKey, exchange);
    }
}
