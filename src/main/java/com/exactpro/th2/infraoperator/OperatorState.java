/*
 * Copyright 2020-2024 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.infraoperator;

import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.unmodifiableSet;

public enum OperatorState {
    INSTANCE;

    private final Map<String, NamespaceState> namespaceStates = new ConcurrentHashMap<>();

    public String getConfigChecksum(String namespace, String key) {
        String checksum = computeIfAbsent(namespace).getConfigMapDataContainer(key).getChecksum();
        return checksum != null ? checksum : "";
    }

    public String getBookName(String namespace) {
        String bookName = computeIfAbsent(namespace).getBookName();
        return bookName != null ? bookName : "";
    }

    public void setBookName(String namespace, String bookName) {
        computeIfAbsent(namespace).setBookName(bookName);
    }

    public void putConfigChecksum(String namespace, String key, String checkSum) {
        computeIfAbsent(namespace).getConfigMapDataContainer(key).setChecksum(checkSum);
    }

    public String getConfigData(String namespace, String key) {
        String checksum = computeIfAbsent(namespace).getConfigMapDataContainer(key).getData();
        return checksum != null ? checksum : "";
    }

    public void putConfigData(String namespace, String key, String data) {
        computeIfAbsent(namespace).getConfigMapDataContainer(key).setData(data);
    }

    public void linkResourceToDictionary(String namespace, String dictionary, String resourceName) {
        computeIfAbsent(namespace).linkResourceToDictionary(dictionary, resourceName);
    }

    public Set<String> getLinkedResourcesForDictionary(String namespace, String dictionary) {
        return computeIfAbsent(namespace).getLinkedResourcesForDictionary(dictionary);
    }

    public NamespaceLock getLock(String namespace) {
        return computeIfAbsent(namespace);
    }

    public Th2CustomResource getResourceFromCache(String name, String namespace) {
        Th2CustomResource resource = computeIfAbsent(namespace).getResource(name);
        if (resource == null) {
            throw new RuntimeException(String.format("Resource \"%s:%s\" not found in cache", namespace, name));
        }
        return resource;
    }

    public void putResourceInCache(Th2CustomResource resource, String namespace) {
        computeIfAbsent(namespace).putResource(resource);
    }

    public void removeResourceFromCache(String name, String namespace) {
        computeIfAbsent(namespace).removeResource(name);
    }

    public HelmRelease getHelmReleaseFromCache(String name, String namespace) {
        return computeIfAbsent(namespace).getHelmRelease(name);
    }

    public void putHelmReleaseInCache(HelmRelease resource, String namespace) {
        computeIfAbsent(namespace).putHelmRelease(resource);
    }

    public void removeHelmReleaseFromCache(String name, String namespace) {
        computeIfAbsent(namespace).removeHelmRelease(name);
    }

    public Collection<HelmRelease> getAllHelmReleases(String namespace) {
        return computeIfAbsent(namespace).getAllHelmReleases();
    }

    public Collection<Th2CustomResource> getAllBoxResources() {
        Collection<Th2CustomResource> resources = new ArrayList<>();
        namespaceStates.values().forEach(namespaceState -> resources.addAll(namespaceState.getAllBoxes()));
        return resources;
    }

    public Collection<String> getNamespaces() {
        return unmodifiableSet(namespaceStates.keySet());
    }

    private NamespaceState computeIfAbsent(String namespace) {
        return namespaceStates.computeIfAbsent(namespace, s -> new NamespaceState());
    }

    public interface NamespaceLock {
        void lock();

        void unlock();
    }
}
