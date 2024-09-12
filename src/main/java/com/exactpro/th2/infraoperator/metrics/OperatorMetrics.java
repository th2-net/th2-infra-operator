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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

public class OperatorMetrics {
    private static final double[] LOCAL_PROCESSING_TIME_BUCKETS = {0.1, 0.2, 0.5, 1.0, 2, 5, 7, 10, 15, 20, 30, 50};

    private static final double[] TOTAL_PROCESSING_TIME_BUCKETS = {5, 10, 20, 30, 40, 50, 60, 70, 80, 120};

    public static final String KEY_DETECTION_TIME = "th2.exactpro.com/detection-time";

    private static final double MILLIS_PER_SECOND = 1000;

    //local metrics
    private static final Gauge EVENT_COUNTER = Gauge
            .build("th2_infra_operator_event_queue", "Amount of events to be processed")
            .labelNames("exported_namespace", "category")
            .register();

    private static final Gauge RESOURCE_CACHE_ERRORS = Gauge
            .build("th2_infra_operator_resource_cache_errors", "Amount of errors in operator resource cache")
            .register();

    private static final Histogram CR_EVENT_PROCESSING_TIME = Histogram
            .build("th2_infra_operator_custom_resource_event_processing_time",
                    "Time it took to process specific event by operator")
            .buckets(LOCAL_PROCESSING_TIME_BUCKETS)
            .labelNames("exported_namespace", "kind", "resName")
            .register();

    private static final Histogram CM_EVENT_PROCESSING_TIME = Histogram
            .build("th2_infra_operator_config_map_event_processing_time",
                    "Time it took to process specific config map")
            .buckets(LOCAL_PROCESSING_TIME_BUCKETS)
            .labelNames("exported_namespace", "resName")
            .register();

    private static final Histogram DICTIONARY_EVENT_PROCESSING_TIME = Histogram
            .build("th2_infra_operator_dictionary_event_processing_time",
                    "Time it took to process dictionary")
            .buckets(LOCAL_PROCESSING_TIME_BUCKETS)
            .labelNames("exported_namespace", "resName")
            .register();

    //total processing time metric
    private static final Histogram EVENT_PROCESSING_TIME_TOTAL = Histogram
            .build("th2_infra_event_processing_total_time",
                    "Time it took to process specific event by both manager and operator")
            .buckets(TOTAL_PROCESSING_TIME_BUCKETS)
            .labelNames("exported_namespace", "kind", "resName")
            .register();

    public static void setPriorityEventCount(int value, String exportedNamespace) {
        EVENT_COUNTER.labels(exportedNamespace, "priority").set(value);
    }

    public static void setRegularEventCount(int value, String exportedNamespace) {
        EVENT_COUNTER.labels(exportedNamespace, "regular").set(value);
    }

    public static Histogram.Timer getCustomResourceEventTimer(HasMetadata resource) {
        String exportedNamespace = resource.getMetadata().getNamespace();
        String resName = resource.getMetadata().getName();
        String kind = resource.getKind();
        return CR_EVENT_PROCESSING_TIME.labels(exportedNamespace, kind, resName).startTimer();
    }

    public static Histogram.Timer getConfigMapEventTimer(HasMetadata resource) {
        String exportedNamespace = resource.getMetadata().getNamespace();
        String resName = resource.getMetadata().getName();
        return CM_EVENT_PROCESSING_TIME.labels(exportedNamespace, resName).startTimer();
    }

    public static Histogram.Timer getDictionaryEventTimer(HasMetadata resource) {
        String exportedNamespace = resource.getMetadata().getNamespace();
        String resName = resource.getMetadata().getName();
        return DICTIONARY_EVENT_PROCESSING_TIME.labels(exportedNamespace, resName).startTimer();
    }

    public static void resetCacheErrors() {
        RESOURCE_CACHE_ERRORS.set(0);
    }

    public static void incrementCacheErrors() {
        RESOURCE_CACHE_ERRORS.inc();
    }

    public static void observeTotal(HasMetadata resource) {
        String detectionTimeStr = resource.getMetadata().getAnnotations().get(KEY_DETECTION_TIME);
        if (detectionTimeStr == null) {
            throw new RuntimeException(KEY_DETECTION_TIME + " can not be null");
        }
        String exportedNamespace = resource.getMetadata().getNamespace();
        String kind = resource.getKind();
        String resName = resource.getMetadata().getName();
        long detectionTime = Long.parseLong(detectionTimeStr);
        double duration = (System.currentTimeMillis() - detectionTime) / MILLIS_PER_SECOND;
        EVENT_PROCESSING_TIME_TOTAL.labels(exportedNamespace, kind, resName).observe(duration);
    }
}
