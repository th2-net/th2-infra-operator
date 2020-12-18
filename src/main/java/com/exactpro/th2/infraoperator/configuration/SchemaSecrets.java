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

package com.exactpro.th2.infraoperator.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Objects;

@JsonDeserialize(builder = SchemaSecrets.SchemaSecretsBuilder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchemaSecrets {

    public static final String DEFAULT_RABBITMQ_SECRET = "rabbitmq";
    public static final String DEFAULT_CASSANDRA_SECRET = "cassandra";

    private String rabbitMQ;
    private String cassandra;

    public SchemaSecrets() {
        this(null, null);
    }

    public SchemaSecrets(String rabbitMQ, String cassandra) {
        this.rabbitMQ = rabbitMQ != null ? rabbitMQ : DEFAULT_RABBITMQ_SECRET;
        this.cassandra = cassandra != null ? cassandra : DEFAULT_CASSANDRA_SECRET;
    }

    public String getRabbitMQ() {
        return rabbitMQ;
    }

    public String getCassandra() {
        return cassandra;
    }

    public static SchemaSecretsBuilder builder() {
        return new SchemaSecretsBuilder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SchemaSecrets)) return false;
        SchemaSecrets that = (SchemaSecrets) o;
        return Objects.equals(getRabbitMQ(), that.getRabbitMQ()) &&
            Objects.equals(getCassandra(), that.getCassandra());
    }

    public static class SchemaSecretsBuilder {

        private String rabbitMQ;
        private String cassandra;

        SchemaSecretsBuilder() {
        }

        public SchemaSecretsBuilder withRabbitMQ(String rabbitMQ) {
            this.rabbitMQ = rabbitMQ;
            return this;
        }

        public SchemaSecretsBuilder withCassandra(String cassandra) {
            this.cassandra = cassandra;
            return this;
        }

        public SchemaSecrets build() {
            return new SchemaSecrets(rabbitMQ, cassandra);
        }
    }
}
