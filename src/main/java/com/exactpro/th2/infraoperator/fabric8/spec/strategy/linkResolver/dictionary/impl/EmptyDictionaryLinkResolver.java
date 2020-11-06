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

package com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.dictionary.impl;

import com.exactpro.th2.infraoperator.fabric8.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.fabric8.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.dictionaries.bunch.DictionaryLinkBunch;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.dictionary.DictionaryLinkResolver;

import java.util.ArrayList;
import java.util.List;

public class EmptyDictionaryLinkResolver implements DictionaryLinkResolver {

    @Override
    public List<DictionaryLinkBunch> resolve(List<Th2Link> linkResources) {
        return new ArrayList<>();
    }

    @Override
    public void resolve(List<Th2Link> linkResources, List<DictionaryLinkBunch> grpcActiveLinks) {

    }

    @Override
    public void resolve(List<Th2Link> linkResources, List<DictionaryLinkBunch> grpcActiveLinks, Th2CustomResource... newResources) {

    }

}
