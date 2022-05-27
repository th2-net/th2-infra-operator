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

package com.exactpro.th2.infraoperator.spec.shared;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.*;

@JsonDeserialize
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)

public class PinSpec {

    private String name;

    private SchemaConnectionType connectionType;

    private PinSettings settings =  null;

    private Set<String> attributes = new HashSet<>();

    private List<Object> filters = new ArrayList<>();

    private List<String> serviceClasses = new ArrayList<>();

    private String serviceClass;

    private String strategy;

    public PinSpec() {
    }

    public String getName() {
        return this.name;
    }

    public SchemaConnectionType getConnectionType() {
        return this.connectionType;
    }

    public PinSettings getSettings() {
        return this.settings;
    }

    public Set<String> getAttributes() {
        return this.attributes;
    }

    public List<Object> getFilters() {
        return this.filters;
    }

    public String getServiceClass() {
        return this.serviceClass;
    }

    public String getStrategy() {
        return this.strategy;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setConnectionType(SchemaConnectionType connectionType) {
        this.connectionType = connectionType;
    }

    public void setAttributes(Set<String> attributes) {
        this.attributes = attributes;
    }

    public void setServiceClasses(List<String> serviceClasses) {
        this.serviceClasses = serviceClasses;
    }

    public void setServiceClass(String serviceClass) {
        this.serviceClass = serviceClass;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public void setSettings(PinSettings settings) {
        this.settings = settings;
    }

    public void setFilters(List<Object> filters) {
        this.filters = filters;
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
        return "PinSpec{" +
                "name='" + name + '\'' +
                ", connectionType=" + connectionType +
                ", settings=" + settings +
                ", attributes=" + attributes +
                ", filters=" + filters +
                ", serviceClasses=" + serviceClasses +
                ", serviceClass='" + serviceClass + '\'' +
                ", strategy='" + strategy + '\'' +
                '}';
    }
}
