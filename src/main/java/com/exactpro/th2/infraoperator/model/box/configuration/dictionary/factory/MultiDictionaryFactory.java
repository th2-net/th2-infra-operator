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

import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.DictionaryEntity;
import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.MultiDictionaryEntity;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;

import java.util.ArrayList;
import java.util.List;

/**
 * A factory that creates list of {@link DictionaryEntity}
 * based on the th2 resource and a list of active links.
 */
public class MultiDictionaryFactory {

    /**
     * Creates a list of {@link DictionaryEntity} based on the th2 resource and a list of active links.
     *
     * @param resource         th2 resource
     * @return list of {@link DictionaryEntity} based on provided active {@code activeLinks} and {@code resource}
     */
    public List<MultiDictionaryEntity> create(Th2CustomResource resource) {

        List<MultiDictionaryEntity> dictionaries = new ArrayList<>();

//        activeMultiLinks.forEach(link -> {
//            try {
//                if (link.getBox().equals(resource.getMetadata().getName())) {
//                    List<MultiDictionaryDescription> dictList = link.getDictionaries();
//                    for (var dict : dictList) {
//                        String alias = dict.getAlias();
//                        String name = dict.getName();
//                        HasMetadata res = OperatorState.INSTANCE
//                                .getResourceFromCache(name, resource.getMetadata().getNamespace());
//
//                        String checksum = ExtractUtils.fullSourceHash(res);
//                        dictionaries.add(new MultiDictionaryEntity(name, checksum, alias));
//                    }
//                }
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//        });

        return dictionaries;
    }
}
