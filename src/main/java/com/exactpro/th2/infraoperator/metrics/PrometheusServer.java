/*
 * Copyright 2020-2024 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.infraoperator.metrics;

import com.exactpro.th2.infraoperator.spec.shared.PrometheusConfiguration;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class PrometheusServer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrometheusServer.class);
    @Nullable
    private final HTTPServer server;

    static {
        DefaultExports.initialize();
    }

    public PrometheusServer(PrometheusConfiguration<String> configuration) throws IOException {
        if (Boolean.parseBoolean(configuration.getEnabled())) {
            String host = configuration.getHost();
            int port = Integer.parseInt(configuration.getPort());
            server = new HTTPServer(host, port);
            LOGGER.info("Started prometheus server on: \"{}:{}\"", host, port);
        } else {
            server = null;
        }
    }

    @Override
    public void close() {
        if (server != null) {
            server.close();
        }
    }
}
