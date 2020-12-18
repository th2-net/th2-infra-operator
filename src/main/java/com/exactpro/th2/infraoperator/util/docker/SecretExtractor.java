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

package com.exactpro.th2.infraoperator.util.docker;

import com.exactpro.th2.infraoperator.model.kubernetes.secret.Secret;
import com.exactpro.th2.infraoperator.model.docker.NexusAuth;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Objects;

import static com.exactpro.th2.infraoperator.util.JsonUtils.JSON_READER;

public final class SecretExtractor {

    private static final Logger logger = LoggerFactory.getLogger(SecretExtractor.class);


    public static final String SECRET_DATA_ALIAS = ".dockerconfigjson";

    public static final String SECRET_COMPONENT_NAME = "th2-solution";


    private SecretExtractor() {
        throw new AssertionError();
    }


    @Nullable
    @SneakyThrows
    public static NexusAuth getNexusAuth(KubernetesClient client, String namespace) {
        Objects.requireNonNull(client);
        Objects.requireNonNull(namespace);

        var secretComponent = client.secrets().inNamespace(namespace).list().getItems().stream()
                .filter(s -> s.getMetadata().getName().equals(SECRET_COMPONENT_NAME))
                .findFirst()
                .orElse(null);

        if (Objects.isNull(secretComponent)) {
            logger.warn("Secret '{}.{}' for extraction auth data for nexus does not exist", namespace, SECRET_COMPONENT_NAME);
            return null;
        }

        var authsBunchEncoded = secretComponent.getData().get(SECRET_DATA_ALIAS);

        if (Objects.isNull(authsBunchEncoded)) {
            logger.warn("No auth data with name '{}' found in secret '{}.{}'", SECRET_DATA_ALIAS, namespace, SECRET_COMPONENT_NAME);
            return null;
        }

        var authsBunchOrigin = new String(Base64.getDecoder().decode(authsBunchEncoded));

        var secret = JSON_READER.readValue(authsBunchOrigin, Secret.class);

        var authBunch = secret.getAuths().entrySet().iterator().next();

        var registry = authBunch.getKey();

        var authBase64 = authBunch.getValue().getData();

        var authOrigin = new String(Base64.getDecoder().decode(authBase64)).split(":");

        return NexusAuth.builder()
                .registry(registry)
                .login(authOrigin[0])
                .password(authOrigin[1])
                .build();
    }

}
