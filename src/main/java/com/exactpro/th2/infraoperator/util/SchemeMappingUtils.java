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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SchemeMappingUtils {

    private static final Logger logger = LoggerFactory.getLogger(SchemeMappingUtils.class);

    private SchemeMappingUtils() {
        throw new AssertionError();
    }

    public static Map<String, FilterConfiguration> specToConfigFieldFilters(List<FieldFilter> fieldFilters) {
        Map<String, FilterConfiguration> filterConfigurationMap = new HashMap<>();
        for (FieldFilter fieldFilter : fieldFilters) {
            FilterConfiguration alreadyPresent = filterConfigurationMap.put(fieldFilter.getFieldName(),
                    FilterConfiguration.builder()
                            .value(fieldFilter.getExpectedValue())
                            .operation(fieldFilter.getOperation())
                            .build());
            if (alreadyPresent != null) {
                logger.warn("Duplicated key \"{}\" detected. will be replaced by the latest occurrence",
                        fieldFilter.getFieldName());
            }
        }
        return filterConfigurationMap;
    }
}
