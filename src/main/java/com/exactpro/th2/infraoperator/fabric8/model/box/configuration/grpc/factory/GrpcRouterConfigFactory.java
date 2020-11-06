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

package com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc.factory;

import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc.GrpcRouterConfiguration;
import com.exactpro.th2.infraoperator.fabric8.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.fabric8.spec.link.relation.boxes.bunch.impl.GrpcLinkBunch;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.PinSpec;

import java.util.List;

/**
 * A factory that creates a grpc configuration
 * based on the th2 resource and a list of active links.
 */
public interface GrpcRouterConfigFactory {

    /**
     * Creates a grpc configuration based on the th2 resource and a list of active links.
     *
     * @param resource th2 resource containing a list of {@link PinSpec}s
     * @param links    active links
     * @return ready grpc configuration based on active {@code links} and specified links in {@code resource}
     */
    GrpcRouterConfiguration createConfig(Th2CustomResource resource, List<GrpcLinkBunch> links);

}
