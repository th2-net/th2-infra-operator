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

package com.exactpro.th2.infraoperator.spec.helmrelease;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;

import java.util.Map;

@SuppressWarnings("unchecked")
@JsonIgnoreProperties(ignoreUnknown = true)
@Group("helm.fluxcd.io")
@Version("v1")
@Kind("HelmRelease")
public class HelmRelease extends CustomResource<HelmReleaseSpec, InstantiableMap> implements Namespaced {

    public static final String ROOT_PROPERTIES_ALIAS = "component";

    public static final int NAME_LENGTH_LIMIT = 26;

    @JsonIgnore
    public Map<String, Object> getValues() {
        return getSpec().getValues();
    }

    @JsonIgnore
    public void addComponentValues(Map<String, Object> newValues) {
        getComponentValuesSection().putAll(newValues);
    }

    @JsonIgnore
    public void addComponentValue(String key, Object newValue) {
        getComponentValuesSection().put(key, newValue);
    }

    @JsonIgnore
    public void addValueSection(Map<String, Object> newSections) {
        getValues().putAll(newSections);
    }

    @JsonIgnore
    public Map<String, Object> getComponentValuesSection() {
        return (Map<String, Object>) getValues().get(ROOT_PROPERTIES_ALIAS);
    }

    public String toString() {
        return "HelmRelease(super=" + super.toString() + ")";
    }
}
