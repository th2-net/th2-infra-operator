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
import com.exactpro.th2.infraoperator.fabric8.model.docker.ImageCompatibility;
import com.exactpro.th2.infraoperator.fabric8.model.docker.ImageManifest;
import com.exactpro.th2.infraoperator.fabric8.model.docker.NexusAuth;
import com.exactpro.th2.infraoperator.fabric8.util.HttpConnectionUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.SneakyThrows;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

public final class DockerUtils {

    private DockerUtils() {
        throw new AssertionError();
    }


    @SneakyThrows
    public static Map<String, String> getImageLabels(Image image, NexusAuth nexusAuth) {
        Objects.requireNonNull(image);
        Objects.requireNonNull(nexusAuth);

        return readImageManifest(HttpConnectionUtils.getNexusConnection(image, nexusAuth).getInputStream()).getHistory().get(0)
                .getV1Compatibility()
                .getConfig()
                .getLabels();
    }

    private static ImageManifest readImageManifest(InputStream inputStream) throws IOException {

        var mapper = new ObjectMapper();

        var module = new SimpleModule();

        module.addDeserializer(ImageCompatibility.class, new JsonDeserializer<>() {
            @Override
            public ImageCompatibility deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonNode node = p.getCodec().readTree(p);
                return new ObjectMapper().readValue(node.asText(), ImageCompatibility.class);
            }
        });

        mapper.registerModule(module);

        return mapper.readValue(inputStream, ImageManifest.class);
    }

}
