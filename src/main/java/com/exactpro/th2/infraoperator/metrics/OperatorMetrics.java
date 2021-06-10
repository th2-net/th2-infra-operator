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

package com.exactpro.th2.infraoperator.metrics;

import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

public class OperatorMetrics {
    private static final double[] DEFAULT_BUCKETS = {0.005, 0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1.0, 2, 5, 10, 20, 50};

    private static Gauge eventCounter = Gauge
            .build("th2_infra_operator_event_queue", "Amount of events to be processed")
            .labelNames("category")
            .register();

    private static Histogram eventProcessingTime = Histogram
            .build("th2_infra_operator_event_processing_time", "Time it took to process specific event")
            .buckets(DEFAULT_BUCKETS)
            .labelNames("kind")
            .register();

    public static void setPriorityEventCount(int value) {
        eventCounter.labels("priority").set(value);
    }

    public static void setRegularEventCount(int value) {
        eventCounter.labels("regular").set(value);
    }

    public static Histogram.Timer getEventTimer(String kind) {
        return eventProcessingTime.labels(kind).startTimer();
    }
}
