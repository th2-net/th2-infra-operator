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

package com.exactpro.th2.infraoperator.spec.strategy.linkResolver.mq;

import com.exactpro.th2.infraoperator.model.box.schema.link.QueueLinkBunch;
import com.exactpro.th2.infraoperator.model.box.schema.link.QueueBunch;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.LinkResolver;

import java.util.List;


public interface QueueLinkResolver extends LinkResolver<QueueLinkBunch> {

    /**
     * Generates active links with already generated
     * corresponding {@link QueueBunch}s based on the th2 link resources.
     */
    List<QueueLinkBunch> resolve(List<Th2Link> linkResources);

}
