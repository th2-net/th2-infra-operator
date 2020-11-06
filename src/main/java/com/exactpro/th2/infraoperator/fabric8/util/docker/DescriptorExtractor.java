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

package com.exactpro.th2.infraoperator.fabric8.util.docker;

import com.exactpro.th2.infraoperator.fabric8.model.docker.Image;
import com.exactpro.th2.infraoperator.fabric8.model.docker.NexusAuth;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class DescriptorExtractor {

    private static final Logger logger = LoggerFactory.getLogger(DescriptorExtractor.class);


    public static final String COMPONENT_DESCRIPTOR_ALIAS = "com.exactpro.th2.descriptor";


    private DescriptorExtractor() {
        throw new AssertionError();
    }


    /**
     * Usage example:
     * <pre>
     * {@code
     * var client = new DefaultKubernetesClient();
     * var namespace = "mit-qa";
     * var imageName = "th2-operator";
     * var imageTag = "test.2";
     *
     * var auth = SecretExtractor.getNexusAuth(client, namespace);
     * var image = Image.builder().name(imageName).tag(imageTag).build();
     * var descriptor = DescriptorExtractor.getImageDescriptor(image, auth);
     * }
     * </pre>
     *
     * @see Image
     * @see SecretExtractor#getNexusAuth
     */
    @Nullable
    public static String getImageDescriptor(Image image, NexusAuth nexusAuth) {
        Objects.requireNonNull(image);
        Objects.requireNonNull(nexusAuth);

        var descriptor = DockerUtils.getImageLabels(image, nexusAuth).get(COMPONENT_DESCRIPTOR_ALIAS);

        if (Objects.isNull(descriptor)) {
            logger.warn("Label with name '{}' was not found in '{}' image",
                    COMPONENT_DESCRIPTOR_ALIAS, image.toString());
            return null;
        }

        return descriptor;
    }

}
