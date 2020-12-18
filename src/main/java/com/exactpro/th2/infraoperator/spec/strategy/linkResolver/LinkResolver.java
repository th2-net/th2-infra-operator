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

package com.exactpro.th2.infraoperator.spec.strategy.linkResolver;

import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;

import java.util.List;

/**
 * Strategy for generating and updating active links between boxes
 */
public interface LinkResolver<T> {

    /**
     * Generates active links
     *
     * @param linkResources the th2 link resources
     * @return generated active links
     */
    List<T> resolve(List<Th2Link> linkResources);

    /**
     * Updates provided active links based on provided th2 link resources
     *
     * @param linkResources   th2 link resources
     * @param grpcActiveLinks links for update
     */
    void resolve(List<Th2Link> linkResources, List<T> grpcActiveLinks);

    /**
     * Updates provided active links based on provided th2 link resources
     * considering a filtering purpose with provided additional resources if needed
     *
     * @param linkResources   th2 link resources
     * @param grpcActiveLinks links for update
     * @param newResources    additional resources if needed
     */
    void resolve(List<Th2Link> linkResources, List<T> grpcActiveLinks, Th2CustomResource... newResources);

}
