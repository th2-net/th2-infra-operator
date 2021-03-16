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

import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.shared.PinSpec;
import com.exactpro.th2.infraoperator.spec.shared.SchemaConnectionType;
import io.fabric8.kubernetes.api.model.HasMetadata;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ExtractUtils {

    private static final Logger logger = LoggerFactory.getLogger(ExtractUtils.class);

    private static final String KEY_SOURCE_HASH = "th2.exactpro.com/source-hash";

    public static final String REFRESH_TOKEN_ALIAS = "refresh-token";

    private ExtractUtils() {
        throw new AssertionError();
    }

    public static String extractFullName(HasMetadata obj) {
        return extractNamespace(obj) + "." + extractName(obj);
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

    public static List<PinSpec> extractMqPins(Th2CustomResource resource) {

        List<PinSpec> mqPins = new ArrayList<>();

        for (var pin : resource.getSpec().getPins()) {
            if (!pin.getConnectionType().equals(SchemaConnectionType.mq)) {
                continue;
            }

            mqPins.add(pin);
        }
        return mqPins;
    }

    public static String extractType(Object object) {
        return object.getClass().getSimpleName();
    }

    @Nullable
    public static String extractOwnerFullName(HasMetadata obj) {
        var ownerReferences = obj.getMetadata().getOwnerReferences();
        if (ownerReferences.size() > 0) {
            return extractNamespace(obj) + "." + ownerReferences.get(0).getName();
        } else {
            logger.warn("[{}<{}>] doesn't have owner resource", extractType(obj), extractFullName(obj));
            return null;
        }
    }

    public static boolean isStorageBox(HasMetadata hasMetadata) {
        return isStorageBox(extractName(hasMetadata));
    }

    public static boolean isStorageBox(String name) {
        return name.equals(StoreHelmTh2Op.MESSAGE_STORAGE_BOX_ALIAS)
                || name.equals(StoreHelmTh2Op.EVENT_STORAGE_BOX_ALIAS);
    }

    public static boolean isStorageResource(HasMetadata hasMetadata) {
        return isStorageResource(extractName(hasMetadata));
    }

    public static boolean isStorageResource(String name) {
        return name.equals(StoreHelmTh2Op.MSG_ST_LINK_RESOURCE_NAME)
                || name.equals(StoreHelmTh2Op.EVENT_ST_LINK_RESOURCE_NAME);
    }

    public static String refreshToken(HasMetadata res) {

        var metadata = res.getMetadata();
        if (metadata == null) {
            return null;
        }

        var annotations = metadata.getAnnotations();
        if (annotations != null) {
            return annotations.get(REFRESH_TOKEN_ALIAS);
        }
        return null;
    }

    private static String sourceHash(HasMetadata res) {

        if (res.getMetadata() != null && res.getMetadata().getAnnotations() != null) {
            return res.getMetadata().getAnnotations().get(KEY_SOURCE_HASH);
        }
        return null;
    }

    public static String sourceHash(HasMetadata res, boolean shortHash) {
        if (!shortHash) {
            return sourceHash(res);
        }

        String hash = ExtractUtils.sourceHash(res);
        if (hash != null) {
            return "[" + hash.substring(0, 8) + "]";
        }
        return "";
    }

}
