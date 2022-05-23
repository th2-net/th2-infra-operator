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

package com.exactpro.th2.infraoperator.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Objects;

@JsonDeserialize(builder = RabbitMQNamespacePermissions.RabbitMQNamespacePermissionsBuilder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RabbitMQNamespacePermissions {

    static final String DEFAULT_CONFIGURE_PERMISSION = "";

    static final String DEFAULT_READ_PERMISSION = ".*";

    static final String DEFAULT_WRITE_PERMISSION = ".*";

    private String configure = DEFAULT_CONFIGURE_PERMISSION;

    private String read = DEFAULT_READ_PERMISSION;

    private String write = DEFAULT_WRITE_PERMISSION;

    public RabbitMQNamespacePermissions() { }

    public RabbitMQNamespacePermissions(String configure, String read, String write) {
        this.configure = configure != null ? configure : DEFAULT_CONFIGURE_PERMISSION;
        this.read = read != null ? read : DEFAULT_READ_PERMISSION;
        this.write = write != null ? write : DEFAULT_WRITE_PERMISSION;
    }

    public String getConfigure() {
        return configure;
    }

    public String getRead() {
        return read;
    }

    public String getWrite() {
        return write;
    }

    public static RabbitMQNamespacePermissionsBuilder builder() {
        return new RabbitMQNamespacePermissionsBuilder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RabbitMQNamespacePermissions)) {
            return false;
        }
        RabbitMQNamespacePermissions that = (RabbitMQNamespacePermissions) o;
        return Objects.equals(getConfigure(), that.getConfigure())
                && Objects.equals(getRead(), that.getRead())
                && Objects.equals(getWrite(), that.getWrite());
    }

    public static class RabbitMQNamespacePermissionsBuilder {

        private String configure;

        private String read;

        private String write;

        RabbitMQNamespacePermissionsBuilder() { }

        public RabbitMQNamespacePermissionsBuilder withConfigure(String configure) {
            this.configure = configure;
            return this;
        }

        public RabbitMQNamespacePermissionsBuilder withRead(String read) {
            this.read = read;
            return this;
        }

        public RabbitMQNamespacePermissionsBuilder withWrite(String write) {
            this.write = write;
            return this;
        }

        public RabbitMQNamespacePermissions build() {
            return new RabbitMQNamespacePermissions(configure, read, write);
        }
    }
}
