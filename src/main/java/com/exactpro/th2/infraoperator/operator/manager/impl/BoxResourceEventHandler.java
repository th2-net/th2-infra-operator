/*
 * Copyright 2020-2022 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import io.fabric8.kubernetes.client.Watcher;

public class BoxResourceEventHandler<T extends Th2CustomResource> extends GenericResourceEventHandler<T> {
    public BoxResourceEventHandler(Watcher<T> watcher, EventQueue eventQueue) {
        super(watcher, eventQueue);
    }

    @Override
    public void onAdd(T obj) {
        OperatorState.INSTANCE.putResourceInCache(obj, obj.getMetadata().getNamespace());
        super.onAdd(obj);
    }

    @Override
    public void onUpdate(T oldObj, T newObj) {
        OperatorState.INSTANCE.putResourceInCache(newObj, newObj.getMetadata().getNamespace());
        super.onUpdate(oldObj, newObj);
    }

    @Override
    public void onDelete(T obj, boolean deletedFinalStateUnknown) {
        OperatorState.INSTANCE.removeResourceFromCache(obj.getMetadata().getName(), obj.getMetadata().getNamespace());
        super.onDelete(obj, deletedFinalStateUnknown);
    }
}
