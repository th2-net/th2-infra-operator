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

package com.exactpro.th2.infraoperator.fabric8.spec;

import com.exactpro.th2.infraoperator.fabric8.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.ParamSpec;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.PinSpec;
import com.exactpro.th2.infraoperator.fabric8.operator.StoreHelmTh2Op;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.DirectionAttribute;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.SchemaConnectionType;
import com.exactpro.th2.infraoperator.fabric8.util.JsonUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import lombok.Data;

import java.util.*;

@Data
@JsonDeserialize
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Th2Spec implements KubernetesResource {

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

    protected List<ParamSpec> params = new ArrayList<>();

    protected List<PinSpec> pins = new ArrayList<>();


    public void setPins(List<PinSpec> pins) {
        this.pins = pins;

        if (Objects.isNull(getPin(StoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS))) {
            var pin = new PinSpec();

            pin.setName(StoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS);
            pin.setConnectionType(SchemaConnectionType.mq);
            pin.setAttributes(Set.of(DirectionAttribute.publish.name(), DirectionAttribute.event.name()));

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

    public OperatorConfig.ChartConfig getChartConfig() {
        return JsonUtils.JSON_READER.convertValue(extendedSettings.get(CHART_CFG_ALIAS), OperatorConfig.ChartConfig.class);
    }

    public PinSpec getPin(String name) {
        return getPins().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

}
