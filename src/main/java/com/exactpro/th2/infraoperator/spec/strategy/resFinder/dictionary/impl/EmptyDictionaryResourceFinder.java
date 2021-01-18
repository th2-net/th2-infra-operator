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

package com.exactpro.th2.infraoperator.spec.strategy.resFinder.dictionary.impl;

import com.exactpro.th2.infraoperator.spec.dictionary.Th2Dictionary;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.dictionary.DictionaryResourceFinder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EmptyDictionaryResourceFinder implements DictionaryResourceFinder {

    @Nullable
    @Override
    public Th2Dictionary getResource(String name, String namespace, Th2Dictionary... additionalSource) {
        return null;
    }

    @Override
    public List<Th2Dictionary> getResources() {
        return List.of();
    }

    @Override
    public List<Th2Dictionary> getResources(String namespace) {
        return List.of();
    }

}
