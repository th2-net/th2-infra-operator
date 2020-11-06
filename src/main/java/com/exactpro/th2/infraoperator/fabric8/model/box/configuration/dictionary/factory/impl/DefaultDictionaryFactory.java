/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.infraoperator.fabric8.model.box.configuration.dictionary.factory.impl;

import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.dictionary.RawDictionary;
import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.dictionary.factory.DictionaryFactory;
import com.exactpro.th2.infraoperator.fabric8.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.dictionaries.bunch.DictionaryLinkBunch;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.resFinder.dictionary.DictionaryResourceFinder;
import com.exactpro.th2.infraoperator.fabric8.util.ArchiveUtils;
import com.exactpro.th2.infraoperator.fabric8.util.ExtractUtils;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DefaultDictionaryFactory implements DictionaryFactory {

    private static final Logger logger = LoggerFactory.getLogger(DefaultDictionaryFactory.class);


    private DictionaryResourceFinder resourceFinder;


    public DefaultDictionaryFactory(DictionaryResourceFinder resourceFinder) {
        this.resourceFinder = resourceFinder;
    }


    @Override
    @SneakyThrows
    public List<RawDictionary> create(Th2CustomResource resource, List<DictionaryLinkBunch> activeLinks) {

        List<RawDictionary> dictionaries = new ArrayList<>();

        for (var link : activeLinks) {
            if (link.getBox().equals(ExtractUtils.extractName(resource))) {
                var dic = link.getDictionary();
                var dicName = dic.getName();
                var dicType = dic.getType();
                var data = resourceFinder.getResource(dicName, ExtractUtils.extractNamespace(resource)).getSpec().getData();
                var encodedData = new String(ArchiveUtils.getGzipBase64StringEncoder().encode(data));
                dictionaries.add(RawDictionary.builder().name(dicName).type(dicType).data(encodedData).build());
            }
        }

        return dictionaries;
    }

}
