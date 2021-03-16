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

import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.shared.PinSpec;
import io.fabric8.kubernetes.api.model.HasMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CustomResourceUtils {

    private static final Logger logger = LoggerFactory.getLogger(CustomResourceUtils.class);

    public static final long RESYNC_TIME = 180000;

    private CustomResourceUtils() {
        throw new AssertionError();
    }

    public static String annotationFor(String namespace, String kind, String resourceName) {
        return String.format("%s:%s/%s", namespace, kind, resourceName);
    }

    public static String annotationFor(HasMetadata resource) {
        return annotationFor(
                resource.getMetadata().getNamespace(),
                resource.getKind(),
                resource.getMetadata().getName()
        );
    }

    public static void removeDuplicatedPins(Th2CustomResource resource) {
        List<PinSpec> pins = resource.getSpec().getPins();
        Map<String, PinSpec> uniquePins = new HashMap<>();
        for (PinSpec pin : pins) {
            if (uniquePins.put(pin.getName(), pin) != null) {
                logger.warn("Detected duplicated pin: \"{}\" in \"{}\". will be substituted by the last ocurrence",
                        pin.getName(), annotationFor(resource));
            }
        }
        resource.getSpec().setPins(new ArrayList<>(uniquePins.values()));
    }
}
