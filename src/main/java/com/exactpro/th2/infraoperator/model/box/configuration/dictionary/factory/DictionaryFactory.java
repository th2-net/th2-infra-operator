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

package com.exactpro.th2.infraoperator.model.box.configuration.dictionary.factory;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.DictionaryEntity;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.DictionaryBinding;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.DictionaryDescription;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import io.fabric8.kubernetes.api.model.HasMetadata;

import java.util.*;

/**
 * A factory that creates list of {@link DictionaryEntity}
 * based on the th2 resource and a list of active links.
 */
public class DictionaryFactory {

    /**
     * Creates a list of {@link DictionaryEntity} based on the th2 resource and a list of active links.
     *
     * @param resource    th2 resource
     * @param activeLinks active links
     * @return list of {@link DictionaryEntity} based on provided active {@code activeLinks} and {@code resource}
     */
    public Collection<DictionaryEntity> create(Th2CustomResource resource, List<DictionaryBinding> activeLinks) {

        Map<String, DictionaryEntity> dictionaries = new HashMap<>();

        activeLinks.forEach(link -> {
            try {
                if (link.getBox().equals(resource.getMetadata().getName())) {
                    DictionaryDescription dict = link.getDictionary();
                    String name = dict.getName();
                    String type = dict.getType();
                    HasMetadata res = OperatorState.INSTANCE
                            .getResourceFromCache(name, resource.getMetadata().getNamespace());

                    String checksum = ExtractUtils.sourceHash(res, false);
                    if (dictionaries.containsKey(type)) {
                        throw new Exception(
                                String.format("multiple dictionaries linked with same type: %s", type)
                        );
                    }
                    dictionaries.put(type, new DictionaryEntity(name, type, checksum));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return dictionaries.values();
    }
}
