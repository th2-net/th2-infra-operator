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

package com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.mq.impl;

import com.exactpro.th2.infraoperator.fabric8.model.box.schema.link.QueueLinkBunch;
import com.exactpro.th2.infraoperator.fabric8.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.fabric8.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.fabric8.spec.strategy.linkResolver.mq.QueueLinkResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


public class EmptyQueueLinkResolver implements QueueLinkResolver {

    private static final Logger logger = LoggerFactory.getLogger(EmptyQueueLinkResolver.class);


    @Override
    public List<QueueLinkBunch> resolve(List<Th2Link> linkResources) {
        return new ArrayList<>();
    }

    @Override
    public void resolve(List<Th2Link> linkResources, List<QueueLinkBunch> grpcActiveLinks) {

    }

    @Override
    public void resolve(List<Th2Link> linkResources, List<QueueLinkBunch> grpcActiveLinks, Th2CustomResource... newResources) {

    }

}
