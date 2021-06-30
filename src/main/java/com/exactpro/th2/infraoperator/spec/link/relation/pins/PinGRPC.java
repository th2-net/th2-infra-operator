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

package com.exactpro.th2.infraoperator.spec.link.relation.pins;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PinGRPC extends AbstractPin {

    private boolean hostNetwork;

    private boolean externalBox;

    private String externalHost;

    private int port;

    public PinGRPC(@JsonProperty("box") String boxName,
                   @JsonProperty("pin") String pinName) {
        super(boxName, pinName);
    }

    public boolean isHostNetwork() {
        return hostNetwork;
    }

    public void setHostNetwork(boolean hostNetwork) {
        this.hostNetwork = hostNetwork;
    }

    public boolean isExternalBox() {
        return externalBox;
    }

    public void setExternalBox(boolean externalBox) {
        this.externalBox = externalBox;
    }

    public String getExternalHost() {
        return externalHost;
    }

    public void setExternalHost(String externalHost) {
        this.externalHost = externalHost;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PinGRPC)) {
            return false;
        }

        return Objects.equals(getBoxName(), ((PinGRPC) o).getBoxName())
                && Objects.equals(getPinName(), ((PinGRPC) o).getPinName());
    }

    @Override
    public int hashCode() {
        throw new AssertionError("method not defined");
    }
}
