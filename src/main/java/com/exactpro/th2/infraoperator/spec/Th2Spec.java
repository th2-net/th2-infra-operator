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
public abstract class Th2Spec implements KubernetesResource {

    private static final String CHART_CFG_ALIAS = "chart-cfg";

    protected String imageName;

    protected String imageVersion;

    protected String type;

    protected String disabled = "false";

    protected Map<String, Object> extendedSettings = new HashMap<>();

    protected Map<String, Object> customConfig = new HashMap<>();

    protected PrometheusConfiguration<String> prometheusConfiguration;

    protected Map<String, Object> mqRouter;

    protected Map<String, Object> grpcRouter;

    protected Map<String, Object> cradleManager;

    protected String bookName;

    protected String loggingConfig;

    protected List<ParamSpec> params = new ArrayList<>();

    protected List<PinSpec> pins = initializeWithEstorePin();

    public Th2Spec() {
    }

    public void setPins(List<PinSpec> pins) {
        this.pins = pins;

        if (Objects.isNull(getPin(StoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS))) {
            var pin = new PinSpec();

            pin.setName(StoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS);
            pin.setConnectionType(SchemaConnectionType.mq);
            pin.setAttributes(Set.of(PinAttribute.publish.name(), PinAttribute.event.name()));

            getPins().add(pin);
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

    public PinSpec getPin(String name) {
        return getPins().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private List<PinSpec> initializeWithEstorePin() {
        List<PinSpec> pins = new ArrayList<>();
        PinSpec pin = new PinSpec();
        pin.setName(StoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS);
        pin.setConnectionType(SchemaConnectionType.mq);
        pin.setAttributes(Set.of(PinAttribute.publish.name(), PinAttribute.event.name()));
        pins.add(pin);
        return pins;
    }

    public String getImageName() {
        return this.imageName;
    }

    public String getImageVersion() {
        return this.imageVersion;
    }

    @JsonProperty("image-name")
    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    @JsonProperty("image-version")
    public void setImageVersion(String imageVersion) {
        this.imageVersion = imageVersion;
    }

    public String getType() {
        return this.type;
    }

    public String getDisabled() {
        return this.disabled;
    }

    public Map<String, Object> getCustomConfig() {
        return this.customConfig;
    }

    @JsonProperty("custom-config")
    public void setCustomConfig(Map<String, Object> customConfig) {
        this.customConfig = customConfig;
    }

    public PrometheusConfiguration<String> getPrometheusConfiguration() {
        return this.prometheusConfiguration;
    }

    @JsonProperty("prometheus")
    public void setPrometheusConfiguration(PrometheusConfiguration<String> prometheusConfiguration) {
        this.prometheusConfiguration = prometheusConfiguration;
    }

    @JsonProperty("extended-settings")
    public void setExtendedSettings(Map<String, Object> extendedSettings) {
        this.extendedSettings = extendedSettings;
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

    public List<PinSpec> getPins() {
        return this.pins;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Th2Spec)) {
            return false;
        }
        Th2Spec th2Spec = (Th2Spec) o;
        return Objects.equals(imageName, th2Spec.imageName) &&
                Objects.equals(imageVersion, th2Spec.imageVersion) &&
                Objects.equals(type, th2Spec.type) &&
                Objects.equals(disabled, th2Spec.disabled) &&
                Objects.equals(extendedSettings, th2Spec.extendedSettings) &&
                Objects.equals(customConfig, th2Spec.customConfig) &&
                Objects.equals(prometheusConfiguration, th2Spec.prometheusConfiguration) &&
                Objects.equals(mqRouter, th2Spec.mqRouter) &&
                Objects.equals(grpcRouter, th2Spec.grpcRouter) &&
                Objects.equals(cradleManager, th2Spec.cradleManager) &&
                Objects.equals(bookName, th2Spec.bookName) &&
                Objects.equals(loggingConfig, th2Spec.loggingConfig) &&
                Objects.equals(params, th2Spec.params) &&
                Objects.equals(pins, th2Spec.pins);
    }

    @Override
    public int hashCode() {
        return Objects.hash(imageName, imageVersion, type, disabled, extendedSettings,
                customConfig, prometheusConfiguration, mqRouter, grpcRouter,
                cradleManager, bookName, loggingConfig, params, pins);
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
                ", params=" + params +
                ", pins=" + pins +
                '}';
    }
}
