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

package com.exactpro.th2.infraoperator;

import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease;
import io.fabric8.kubernetes.api.model.HasMetadata;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class NamespaceState implements OperatorState.NamespaceLock {
    private final Map<String, ConfigMapDataContainer> configMapDataContainerMap = new HashMap<>();

    private final Map<String, HasMetadata> resources = new HashMap<>();

    private final Map<String, HelmRelease> helmReleaseMap = new HashMap<>();

    private final Map<String, Set<String>> dictionariesMap = new HashMap<>();

    private String bookName;

    private final Lock lock = new ReentrantLock(true);

    @Override
    public void lock() {
        lock.lock();
    }

    @Override
    public void unlock() {
        lock.unlock();
    }

    public ConfigMapDataContainer getConfigMapDataContainer(String key) {
        return configMapDataContainerMap.computeIfAbsent(key, k -> new ConfigMapDataContainer());
    }

    public String getBookName() {
        return bookName;
    }

    public void setBookName(String bookName) {
        this.bookName = bookName;
    }

    public HasMetadata getResource(String resName) {
        return resources.get(resName);
    }

    public void putResource(HasMetadata resource) {
        String resName = resource.getMetadata().getName();
        resources.put(resName, resource);
    }

    public void removeResource(String resName) {
        resources.remove(resName);
    }

    public HelmRelease getHelmRelease(String resName) {
        return helmReleaseMap.get(resName);
    }

    public void putHelmRelease(HelmRelease resource) {
        String resName = resource.getMetadata().getName();
        helmReleaseMap.put(resName, resource);
    }

    public void removeHelmRelease(String resName) {
        helmReleaseMap.remove(resName);
    }

    public Collection<HelmRelease> getAllHelmReleases() {
        return helmReleaseMap.values();
    }

    public void linkResourceToDictionary(String dictionary, String resourceName) {
        dictionariesMap.computeIfAbsent(dictionary, key -> new HashSet<>()).add(resourceName);
    }

    public Set<String> getLinkedResourcesForDictionary(String dictionary) {
        return dictionariesMap.get(dictionary);
    }

    @Override
    public boolean equals(final Object o) {
        throw new AssertionError("method not supported");
    }

    @Override
    public int hashCode() {
        throw new AssertionError("method not supported");
    }

    static final class ConfigMapDataContainer {
        private String checksum;

        private String data;

        public String getChecksum() {
            return checksum;
        }

        public String getData() {
            return data;
        }

        public void setChecksum(String checksum) {
            this.checksum = checksum;
        }

        public void setData(String data) {
            this.data = data;
        }
    }
}
