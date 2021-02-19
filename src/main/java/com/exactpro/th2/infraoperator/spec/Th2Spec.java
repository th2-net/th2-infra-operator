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
import com.exactpro.th2.infraoperator.util.JsonUtils;
import com.exactpro.th2.infraoperator.spec.shared.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Data
@JsonDeserialize
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Th2Spec implements KubernetesResource {

    private static final Logger logger = LoggerFactory.getLogger(Th2Spec.class);

    private static final String CHART_CFG_ALIAS = "chart-cfg";

    @JsonProperty("image-name")
    protected String imageName;

    @JsonProperty("image-version")
    protected String imageVersion;

    protected String type;

    @JsonProperty("extended-settings")
    protected Map<String, Object> extendedSettings = new HashMap<>();

    @JsonProperty("custom-config")
    protected Map<String, Object> customConfig = new HashMap<>();

    @JsonProperty("prometheus")
    protected PrometheusConfiguration prometheusConfiguration;

    protected List<ParamSpec> params = new ArrayList<>();

    protected List<PinSpec> pins = new ArrayList<>();

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

    public Map<String, Object> getExtendedSettingsOrigin() {
        return extendedSettings;
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


    public void removeDuplicatedPins(String resourceName){
        Map<String, PinSpec> pinsMap = new HashMap<>();
        for(PinSpec pin: pins){
            PinSpec oldValue = pinsMap.put(pin.getName(), pin);
            if(oldValue != null){
                logger.warn("Found duplicated pin: '{}.{}'. Rewriting with later occurrence.", resourceName, pin.getName());
            }
        }
        this.pins = new ArrayList<>(pinsMap.values());
    }
}
