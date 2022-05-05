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

package com.exactpro.th2.infraoperator.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.shared.PinSpec;
import com.exactpro.th2.infraoperator.spec.shared.SchemaConnectionType;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class ExtractUtils {

    static final String KEY_SOURCE_HASH = "th2.exactpro.com/source-hash";

    public static final String REFRESH_TOKEN_ALIAS = "refresh-token";

    private ExtractUtils() {
    }

    public static String extractName(HasMetadata obj) {
        return obj.getMetadata().getName();
    }

    public static String extractNamespace(HasMetadata obj) {
        return obj.getMetadata().getNamespace();
    }

    public static Map<String, String> extractAnnotations(HasMetadata obj) {
        return obj.getMetadata().getAnnotations();
    }

    public static Map<String, String> extractNeededAnnotations(HasMetadata obj, String antecedent, String commitHash) {
        Map<String, String> neededAnnotations = new HashMap<>();
        var resourceAnnotations = obj.getMetadata().getAnnotations();
        neededAnnotations.put(antecedent, resourceAnnotations.get(antecedent));
        neededAnnotations.put(commitHash, resourceAnnotations.get(commitHash));
        return neededAnnotations;
    }

    public static List<PinSpec> extractMqPins(Th2CustomResource resource) {
        List<PinSpec> mqPins = new ArrayList<>();

        for (var pin : resource.getSpec().getPins()) {
            if (pin.getConnectionType().equals(SchemaConnectionType.mq)) {
                mqPins.add(pin);
            }
        }
        return mqPins;
    }

    public static String extractType(Object object) {
        return object.getClass().getSimpleName();
    }

    public static String refreshToken(HasMetadata res) {
        var metadata = res.getMetadata();
        if (metadata == null) {
            return null;
        }

        var annotations = metadata.getAnnotations();
        return annotations != null ? annotations.get(REFRESH_TOKEN_ALIAS) : null;
    }

    public static String fullSourceHash(HasMetadata res) {
        if (res.getMetadata() != null && res.getMetadata().getAnnotations() != null) {
            return res.getMetadata().getAnnotations().get(KEY_SOURCE_HASH);
        }
        return "";
    }

    public static String shortSourceHash(HasMetadata res) {
        String fullHash = fullSourceHash(res);
        if (Strings.isNullOrEmpty(fullHash)) {
            return fullHash;
        }
        return "[" + fullHash.substring(0, 8) + "]";
    }

}
