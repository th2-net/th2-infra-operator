/*
 * Copyright 2021-2024 Exactpro (Exactpro Systems Limited)
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
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.jupiter.api.Test;

import static com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease.NAME_LENGTH_LIMIT;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.digest;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.extractHashedName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomResourceUtilsTests {

    private static final String SOURCE_NAMESPACE = "namespace";

    private static final String SOURCE_NAME = "123456789_123456789_123456789";

    @Test
    void extractHashedNameTest() {
        var currentName = SOURCE_NAME.substring(0, NAME_LENGTH_LIMIT - 1);
        assertEquals(currentName, extractHashedName(createTh2CustomResource(currentName)));

        currentName = SOURCE_NAME.substring(0, NAME_LENGTH_LIMIT);
        assertEquals(digest(currentName), extractHashedName(createTh2CustomResource(currentName)));

        currentName = SOURCE_NAME.substring(0, NAME_LENGTH_LIMIT + 1);
        assertEquals(digest(currentName), extractHashedName(createTh2CustomResource(currentName)));
    }

    private Th2CustomResource createTh2CustomResource(String name) {
        return createResource(Th2CustomResource.class, name);
    }
    
    private <T extends HasMetadata> T createResource(Class<T> instanceClass, String name) {
        T resource = mock(instanceClass);
        ObjectMeta metaData = mock(ObjectMeta.class);
        when(metaData.getNamespace()).thenReturn(SOURCE_NAMESPACE);
        when(metaData.getName()).thenReturn(name);

        when(resource.getMetadata()).thenReturn(metaData);

        return resource;
    }
}
