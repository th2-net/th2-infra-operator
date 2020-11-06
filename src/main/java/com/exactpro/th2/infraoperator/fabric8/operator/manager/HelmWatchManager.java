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

package com.exactpro.th2.infraoperator.fabric8.operator.manager;

import com.exactpro.th2.infraoperator.fabric8.operator.HelmReleaseTh2Op;
import com.exactpro.th2.infraoperator.fabric8.operator.context.HelmOperatorContext;
import com.exactpro.th2.infraoperator.fabric8.spec.Th2CustomResource;

import java.util.function.Function;


/**
 * Allows you to conveniently manage the operator's watching of batch
 * of custom resources and the definitions of their parameters.
 */
public interface HelmWatchManager {

    /**
     * Starts watching all provided resources
     */
    void startWatching();

    /**
     * Stops watching all provided resources
     * and starts watching again including those resources
     * that were added after the previous start
     */
    void resetWatching();

    /**
     * Stops watching all provided resources
     */
    void stopWatching();

    /**
     * @return the status of the manager: is it watching all resources?
     */
    boolean isWatching();

    /**
     * Adds target for watching
     *
     * @param watcher callback function providing an aggregated set
     *                of parameters(context) for each resource watcher
     * @param <T>     type of custom resource
     */
    <T extends Th2CustomResource> void addTarget(Function<HelmOperatorContext.Builder<?, ?>, HelmReleaseTh2Op<T>> watcher);

}
