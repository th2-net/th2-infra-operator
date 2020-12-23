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

package com.exactpro.th2.infraoperator.model.box.configuration.mq.factory;

import com.exactpro.th2.infraoperator.model.box.configuration.mq.MessageRouterConfiguration;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.shared.PinSpec;

/**
 * A factory that creates a mq configuration
 * based on the th2 resource and a list of active links.
 */
public interface MessageRouterConfigFactory {

    /**
     * Creates a mq configuration based on the th2 resource and a list of active links.
     *
     * @param resource th2 resource containing a list of {@link PinSpec}s
     * @return ready mq configuration based on active {@code links} and specified links in {@code resource}
     */
    MessageRouterConfiguration createConfig(Th2CustomResource resource);

}