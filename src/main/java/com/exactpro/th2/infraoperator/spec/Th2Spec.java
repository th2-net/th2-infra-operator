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

package com.exactpro.th2.infraoperator.spec;

import com.exactpro.th2.infraoperator.configuration.ChartConfig;
import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op;
import com.exactpro.th2.infraoperator.spec.shared.*;
import com.exactpro.th2.infraoperator.spec.shared.pin.*;
import com.exactpro.th2.infraoperator.util.JsonUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;

import java.util.*;

@JsonDeserialize
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Th2Spec implements KubernetesResource {

    private static final String CHART_CFG_ALIAS = "chartCfg";

    protected String imageName;

    protected String imageVersion;

    protected String type;

    protected boolean disabled = false;

    protected Map<String, Object> extendedSettings = new HashMap<>();

    protected Map<String, Object> customConfig = new HashMap<>();

    @JsonProperty("prometheus")
    protected PrometheusConfiguration<String> prometheusConfiguration;

    protected Map<String, Object> mqRouter;

    protected Map<String, Object> grpcRouter;

    protected Map<String, Object> cradleManager;

    protected String bookName;

    protected String loggingConfig;

    protected PinSpec pins = initializeWithEstorePin();

    public Th2Spec() {
    }

    public void setPins(PinSpec pins) {
        this.pins = pins;

        if (Objects.isNull(getPins().getMqPin(StoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS))) {
            MqPin pin = new MqPin(StoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS,
                    Set.of(PinAttribute.publish.name(), PinAttribute.event.name()));
            getPins().getMq().add(pin);
        }
    }

    public Map<String, Object> getExtendedSettings() {
        var copy = new HashMap<>(extendedSettings);
        copy.remove(CHART_CFG_ALIAS);
        return copy;
    }

    public ChartConfig getChartConfig() {
        return JsonUtils.JSON_READER.convertValue(extendedSettings.get(CHART_CFG_ALIAS), ChartConfig.class);
    }

    private PinSpec initializeWithEstorePin() {
        PinSpec pins = new PinSpec();
        MqPin pin = new MqPin(StoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS,
                Set.of(PinAttribute.publish.name(), PinAttribute.event.name())
        );
        pins.getMq().add(pin);
        return pins;
    }

    public String getImageName() {
        return this.imageName;
    }

    public String getImageVersion() {
        return this.imageVersion;
    }

    public String getType() {
        return this.type;
    }

    public boolean getDisabled() {
        return this.disabled;
    }

    public Map<String, Object> getCustomConfig() {
        return this.customConfig;
    }

    public PrometheusConfiguration<String> getPrometheusConfiguration() {
        return this.prometheusConfiguration;
    }

    public Map<String, Object> getMqRouter() {
        return this.mqRouter;
    }

    public Map<String, Object> getGrpcRouter() {
        return this.grpcRouter;
    }

    public Map<String, Object> getCradleManager() {
        return this.cradleManager;
    }

    public String getBookName() {
        return this.bookName;
    }

    public String getLoggingConfig() {
        return this.loggingConfig;
    }

    public PinSpec getPins() {
        return this.pins;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        throw new AssertionError("method not defined");
    }

    @Override
    public int hashCode() {
        throw new AssertionError("method not defined");
    }

    @Override
    public String toString() {
        return "Th2Spec{" +
                "imageName='" + imageName + '\'' +
                ", imageVersion='" + imageVersion + '\'' +
                ", type='" + type + '\'' +
                ", disabled='" + disabled + '\'' +
                ", extendedSettings=" + extendedSettings +
                ", customConfig=" + customConfig +
                ", prometheusConfiguration=" + prometheusConfiguration +
                ", mqRouter=" + mqRouter +
                ", grpcRouter=" + grpcRouter +
                ", cradleManager=" + cradleManager +
                ", bookName='" + bookName + '\'' +
                ", loggingConfig='" + loggingConfig + '\'' +
                ", pins=" + pins +
                '}';
    }
}
