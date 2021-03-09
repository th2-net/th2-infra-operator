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

import io.fabric8.kubernetes.api.model.HasMetadata;


public final class CustomResourceUtils {

    public static long RESYNC_TIME = 180000;

    private CustomResourceUtils() {
        throw new AssertionError();
    }


    public static String annotationFor(String namespace, String kind, String resourceName) {
        return String.format("%s:%s/%s", namespace, kind, resourceName);
    }

    public static String annotationFor(HasMetadata resource) {
        return annotationFor(
                  resource.getMetadata().getNamespace()
                , resource.getKind()
                , resource.getMetadata().getName()
        );
    }
}
