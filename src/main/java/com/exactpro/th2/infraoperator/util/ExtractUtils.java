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

package com.exactpro.th2.infraoperator.util;

import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.shared.PinSpec;
import com.exactpro.th2.infraoperator.operator.AbstractTh2Operator;
import com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op;
import com.exactpro.th2.infraoperator.spec.shared.SchemaConnectionType;
import io.fabric8.kubernetes.api.model.HasMetadata;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ExtractUtils {

    private static final Logger logger = LoggerFactory.getLogger(ExtractUtils.class);


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

    public static Long extractGeneration(HasMetadata obj) {
        return obj.getMetadata().getGeneration();
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

    @Nullable
    public static String extractRefreshToken(HasMetadata obj) {
        var annotations = obj.getMetadata().getAnnotations();

        if (Objects.nonNull(annotations)) {
            return annotations.get(AbstractTh2Operator.REFRESH_TOKEN_ALIAS);
        }

        return null;
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

    public static boolean compareRefreshTokens(HasMetadata objFirst, HasMetadata objSecond) {
        var firstRT = extractRefreshToken(objFirst);
        var secondRT = extractRefreshToken(objSecond);
        return Objects.isNull(firstRT) && Objects.isNull(secondRT) ||
                Objects.nonNull(firstRT) && Objects.nonNull(secondRT) && firstRT.equals(secondRT);
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

}
