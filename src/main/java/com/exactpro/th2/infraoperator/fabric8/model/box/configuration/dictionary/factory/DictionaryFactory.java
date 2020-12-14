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

package com.exactpro.th2.infraoperator.fabric8.model.box.configuration.dictionary.factory;

import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.dictionary.DictionaryEntity;
import com.exactpro.th2.infraoperator.fabric8.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.dictionaries.bunch.DictionaryLinkBunch;

import java.util.List;

/**
 * A factory that creates list of {@link DictionaryEntity}
 * based on the th2 resource and a list of active links.
 */
public interface DictionaryFactory {

    /**
     * Creates a list of {@link DictionaryEntity} based on the th2 resource and a list of active links.
     *
     * @param resource th2 resource
     * @param links    active links
     * @return list of {@link DictionaryEntity} based on provided active {@code activeLinks} and {@code resource}
     */
    List<DictionaryEntity> create(Th2CustomResource resource, List<DictionaryLinkBunch> links);

}
