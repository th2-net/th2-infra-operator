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

package com.exactpro.th2.infraoperator.operator.helm;

import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingMQ;

import java.util.ArrayList;
import java.util.List;

public class StorageTh2LinksUpdater<CR extends Th2CustomResource> extends StorageTh2LinksRefresher<CR> {

    public StorageTh2LinksUpdater(StorageContext context) {
        super(context);
    }

    @Override
    protected List<PinCouplingMQ> update(List<PinCouplingMQ> oldHiddenLinks, List<PinCouplingMQ> newHiddenLinks) {
        List<PinCouplingMQ> updated = new ArrayList<>(oldHiddenLinks);

        for (var newLink : newHiddenLinks) {
            if (!updated.contains(newLink)) {
                updated.add(newLink);
            }
        }

        return updated;
    }

}
