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

package com.exactpro.th2.infraoperator.model.box.configuration.dictionary.factory.impl;

import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.DictionaryEntity;
import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.factory.DictionaryFactory;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.dictionary.Th2Dictionary;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.DictionaryBinding;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.DictionaryDescription;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.dictionary.DictionaryResourceFinder;
import com.exactpro.th2.infraoperator.util.ArchiveUtils;

import java.util.ArrayList;
import java.util.List;

public class DefaultDictionaryFactory implements DictionaryFactory {

    private DictionaryResourceFinder resourceFinder;

    public DefaultDictionaryFactory(DictionaryResourceFinder resourceFinder) {
        this.resourceFinder = resourceFinder;
    }

    @Override
    public List<DictionaryEntity> create(Th2CustomResource resource, List<DictionaryBinding> activeLinks) {

        List<DictionaryEntity> dictionaries = new ArrayList<>();

        activeLinks.forEach(link -> {
            try {
                if (link.getBox().equals(resource.getMetadata().getName())) {
                    DictionaryDescription dict = link.getDictionary();
                    String name = dict.getName();
                    String type = dict.getType();
                    Th2Dictionary res = resourceFinder.getResource(name, resource.getMetadata().getNamespace());

                    String encodedData;
                    if (res.getSpec().isCompressed()) {
                        encodedData = res.getSpec().getData();
                    } else {
                        encodedData = new String(ArchiveUtils.getGZIPBase64Encoder().encodeString(res.getSpec().getData()));

                    }

                    dictionaries.add(DictionaryEntity.builder()
                            .setName(name)
                            .setType(type)
                            .setData(encodedData)
                            .build());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return dictionaries;
    }
}
