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

import com.exactpro.th2.infraoperator.spec.shared.*;
import com.exactpro.th2.infraoperator.spec.shared.pin.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;

import java.util.*;

@JsonDeserialize
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Th2Spec implements KubernetesResource {
    protected String imageName;

    protected String imageVersion;

    protected String type;

    protected boolean disabled;

    protected Map<String, Object> extendedSettings = new HashMap<>();

    protected Map<String, Object> customConfig = new HashMap<>();

    protected PrometheusConfiguration<Boolean> prometheus;

    protected Map<String, Object> mqRouter;

    protected Map<String, Object> grpcRouter;

    protected Map<String, Object> cradleManager;

    protected String bookName;

    protected String loggingConfig;

    protected PinSpec pins = new PinSpec();

    public Th2Spec() {
    }

    public Map<String, Object> getExtendedSettings() {
        return new HashMap<>(extendedSettings);
    }

    public String getImageName() {
        return this.imageName;
    }

    public String getImageVersion() {
        return this.imageVersion;
    }

    public boolean getDisabled() {
        return this.disabled;
    }

    public Map<String, Object> getCustomConfig() {
        return this.customConfig;
    }

    public PrometheusConfiguration<Boolean> getPrometheus() {
        return this.prometheus;
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

    public String getType() {
        return type;
    }

    public boolean isDisabled() {
        return disabled;
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
                ", prometheusConfiguration=" + prometheus +
                ", mqRouter=" + mqRouter +
                ", grpcRouter=" + grpcRouter +
                ", cradleManager=" + cradleManager +
                ", bookName='" + bookName + '\'' +
                ", loggingConfig='" + loggingConfig + '\'' +
                ", pins=" + pins +
                '}';
    }
}
