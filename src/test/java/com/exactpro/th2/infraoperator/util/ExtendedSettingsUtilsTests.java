/*
 * Copyright 2021-2021 Exactpro (Exactpro Systems Limited)
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

import static com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease.NAME_LENGTH_LIMIT;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.digest;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.extractHashedName;
import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.search;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;

public class ExtendedSettingsUtilsTests {

    private final String sourceNamespace = "namespace";
    private final String sourceName = "123456789_123456789_123456789";

    @Test
    public void extractHashedNameTest() {
        var currentName = sourceName.substring(0, NAME_LENGTH_LIMIT - 1);
        assertEquals(currentName, extractHashedName(createTh2CustomResource(currentName)));

        currentName = sourceName.substring(0, NAME_LENGTH_LIMIT);
        assertEquals(digest(currentName), extractHashedName(createTh2CustomResource(currentName)));

        currentName = sourceName.substring(0, NAME_LENGTH_LIMIT + 1);
        assertEquals(digest(currentName), extractHashedName(createTh2CustomResource(currentName)));
    }

    @Test
    public void searchTest() {
        List<HelmRelease> helmReleases = new ArrayList<>();
        helmReleases.add(createHelmRelease(sourceName.substring(0, NAME_LENGTH_LIMIT - 1)));
        helmReleases.add(createHelmRelease(digest(sourceName.substring(0, NAME_LENGTH_LIMIT))));
        helmReleases.add(createHelmRelease(digest(sourceName.substring(0, NAME_LENGTH_LIMIT + 1))));

        var th2CustomResource = createTh2CustomResource(sourceName.substring(0, NAME_LENGTH_LIMIT - 1));
        assertEquals(helmReleases.get(0), search(helmReleases, th2CustomResource));

        th2CustomResource = createTh2CustomResource(sourceName.substring(0, NAME_LENGTH_LIMIT));
        assertEquals(helmReleases.get(1), search(helmReleases, th2CustomResource));

        th2CustomResource = createTh2CustomResource(sourceName.substring(0, NAME_LENGTH_LIMIT + 1));
        assertEquals(helmReleases.get(2), search(helmReleases, th2CustomResource));
    }

    private HelmRelease createHelmRelease(String name) {
        HelmRelease helmRelease = createResource(HelmRelease.class, name);

        List<OwnerReference> ownerReferences = new ArrayList<>();
        var ownerReference = new OwnerReference();
        ownerReference.setName(name);
        ownerReferences.add(ownerReference);

        when(helmRelease.getMetadata().getOwnerReferences()).thenReturn(ownerReferences);

        return helmRelease;
    }
    
    private Th2CustomResource createTh2CustomResource(String name) {
        return createResource(Th2CustomResource.class, name);
    }
    
    private <T extends HasMetadata> T createResource(Class<T> instanceClass, String name) {
        T resource = mock(instanceClass);
        ObjectMeta metaData = mock(ObjectMeta.class);
        when(metaData.getNamespace()).thenReturn(sourceNamespace);
        when(metaData.getName()).thenReturn(name);

        when(resource.getMetadata()).thenReturn(metaData);

        return resource;
    }
}
