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

package com.exactpro.th2.infraoperator.util;

import com.exactpro.th2.infraoperator.model.box.configuration.mq.FilterConfiguration;
import com.exactpro.th2.infraoperator.spec.shared.FieldFilter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class SchemeMappingUtils {

    private SchemeMappingUtils() {
        throw new AssertionError();
    }

    public static Map<String, FilterConfiguration> specToConfigFieldFilters(List<FieldFilter> fieldFilters) {
        return fieldFilters.stream()
                .collect(Collectors.toMap(
                        FieldFilter::getFieldName,
                        ff -> FilterConfiguration.builder()
                                .value(ff.getExpectedValue())
                                .operation(ff.getOperation())
                                .build()
                ));
    }

}
