/*
 * Copyright 2022 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.infraoperator.spec.shared.pin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GrpcClientPin implements Th2Pin {
    private String name;

    private Set<String> attributes = new HashSet<>();

    private String serviceClass;

    private String strategy;

    private List<Object> filters = new ArrayList<>();

    @Override
    public String getName() {
        return name;
    }

    public Set<String> getAttributes() {
        return attributes;
    }

    public String getServiceClass() {
        return serviceClass;
    }

    public String getStrategy() {
        return strategy;
    }

    public List<Object> getFilters() {
        return filters;
    }
}
