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

package com.exactpro.th2.infraoperator.spec.shared;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Objects;

@JsonDeserialize
public final class PrometheusConfiguration<E> {

    private String host;

    private String port;

    private E enabled;

    public PrometheusConfiguration() {}

    public PrometheusConfiguration(String host, String port, E enabled) {
        this.host = host;
        this.port = port;
        this.enabled = enabled;
    }

    public static <E> PrometheusConfiguration<E> createDefault(E enabled) {
        return new PrometheusConfiguration<E>(null, null, enabled);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PrometheusConfiguration)) {
            return false;
        }
        PrometheusConfiguration that = (PrometheusConfiguration) o;
        return Objects.equals(host, that.host) &&
                Objects.equals(port, that.port) &&
                Objects.equals(enabled, that.enabled);
    }

    public String getHost() {
        return this.host;
    }

    public String getPort() {
        return this.port;
    }

    public E getEnabled() {
        return this.enabled;
    }
}
