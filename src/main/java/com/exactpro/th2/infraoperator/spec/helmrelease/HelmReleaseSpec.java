/*
 * Copyright 2020-2022 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.infraoperator.spec.helmrelease;

import com.exactpro.th2.infraoperator.configuration.fields.ChartConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;

import java.util.Map;

@JsonDeserialize
@JsonIgnoreProperties(ignoreUnknown = true)
public class HelmReleaseSpec implements KubernetesResource {

    private int maxHistory;

    private String releaseName;

    private String interval;

    private ChartConfig chart;

    private Map<String, Object> values;

    public int getMaxHistory() {
        return maxHistory;
    }

    public String getReleaseName() {
        return releaseName;
    }

    public String getInterval() {
        return interval;
    }

    public ChartConfig getChart() {
        return chart;
    }

    public void setChart(ChartConfig chart) {
        this.chart = chart;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public void setReleaseName(String releaseName) {
        this.releaseName = releaseName;
    }
}
