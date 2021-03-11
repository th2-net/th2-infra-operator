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

package com.exactpro.th2.infraoperator.spec.strategy.linkResolver.dictionary.impl;

import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.dictionary.Th2Dictionary;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.DictionaryBinding;
import com.exactpro.th2.infraoperator.spec.link.validator.ValidationStatus;
import com.exactpro.th2.infraoperator.spec.link.validator.chain.impl.DicBoxResourceExist;
import com.exactpro.th2.infraoperator.spec.link.validator.chain.impl.DictionaryResourceExist;
import com.exactpro.th2.infraoperator.spec.link.validator.model.DictionaryLinkContext;
import com.exactpro.th2.infraoperator.spec.link.validator.model.DirectionalLinkContext;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.GenericLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.dictionary.DictionaryLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.box.BoxResourceFinder;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.dictionary.DictionaryResourceFinder;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DefaultDictionaryLinkResolver extends GenericLinkResolver<DictionaryBinding> implements DictionaryLinkResolver {

    private static final Logger logger = LoggerFactory.getLogger(DefaultDictionaryLinkResolver.class);


    private final BoxResourceFinder boxResourceFinder;

    private final DictionaryResourceFinder dicResourceFinder;


    public DefaultDictionaryLinkResolver(
            BoxResourceFinder boxResourceFinder,
            DictionaryResourceFinder dicResourceFinder
    ) {
        this.boxResourceFinder = boxResourceFinder;
        this.dicResourceFinder = dicResourceFinder;
    }

    @Override
    public void resolve(List<Th2Link> linkResources, List<DictionaryBinding> dicActiveLinks, Th2CustomResource... newResources) {
        dicActiveLinks.clear();

        for (var lRes : linkResources) {
            for (var link : lRes.getSpec().getDictionariesRelation()) {

                if (validateLinks(lRes, link, newResources)) {
                    dicActiveLinks.add(link);
                }

            }
        }

    }

    private boolean validateLinks(Th2Link linkRes, DictionaryBinding link, Th2CustomResource... additionalSource) {

        var namespace = ExtractUtils.extractNamespace(linkRes);

        var boxName = link.getBox();

        var boxContext = DirectionalLinkContext.builder()
                .boxName(boxName)
                .linkName(link.getName())
                .linkResName(ExtractUtils.extractName(linkRes))
                .linkNamespace(namespace)
                .build();

        var dicName = link.getDictionary().getName();

        var dicContextTemplate = DictionaryLinkContext.builder()
                .dictionary(dicName)
                .linkName(link.getName())
                .linkResName(ExtractUtils.extractName(linkRes))
                .linkNamespace(namespace)
                .build();


        var boxRes = boxResourceFinder.getResource(boxName, namespace, additionalSource);

        var boxValRes = validateResourceByDirectionalLink(boxRes, boxContext);


        var dicRes = dicResourceFinder.getResource(dicName, namespace);

        var dicValRes = validateDictionaryByDirectionalLink(dicRes, dicContextTemplate);


        return boxValRes.equals(ValidationStatus.VALID) && dicValRes.equals(ValidationStatus.VALID);
    }

    private ValidationStatus validateResourceByDirectionalLink(Th2CustomResource resource, DirectionalLinkContext context) {
        return new DicBoxResourceExist(context).validate(resource);
    }

    private ValidationStatus validateDictionaryByDirectionalLink(Th2Dictionary resource, DictionaryLinkContext context) {
        return new DictionaryResourceExist(context).validate(resource);
    }

}
