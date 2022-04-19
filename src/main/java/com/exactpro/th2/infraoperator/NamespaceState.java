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
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.DictionaryBinding;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.MultiDictionaryBinding;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingGRPC;
import io.fabric8.kubernetes.api.model.HasMetadata;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class NamespaceState implements OperatorState.NamespaceLock {
    private List<Th2Link> linkResources = new ArrayList<>();

    private final Map<String, ConfigMapDataContainer> configMapDataContainerMap = new HashMap<>();

    private final Map<String, HasMetadata> resources = new HashMap<>();

    private final Map<String, HelmRelease> helmReleaseMap = new HashMap<>();

    private final Lock lock = new ReentrantLock(true);

    @Override
    public void lock() {
        lock.lock();
    }

    @Override
    public void unlock() {
        lock.unlock();
    }

    public List<Th2Link> getLinkResources() {
        return this.linkResources;
    }

    public List<PinCouplingGRPC> getGrpcLinks() {
        List<PinCouplingGRPC> grpcLinks = new ArrayList<>();
        for (var linkRes : linkResources) {
            grpcLinks.addAll(linkRes.getSpec().getBoxesRelation().getRouterGrpc());
        }
        return grpcLinks;
    }

    public List<DictionaryBinding> getDictionaryLinks() {
        List<DictionaryBinding> dictionaryBindings = new ArrayList<>();
        for (var linkRes : linkResources) {
            dictionaryBindings.addAll(linkRes.getSpec().getDictionariesRelation());
        }
        return dictionaryBindings;
    }

    public List<MultiDictionaryBinding> getMultiDictionaryLinks() {
        List<MultiDictionaryBinding> multiDictionaryBindings = new ArrayList<>();
        for (var linkRes : linkResources) {
            multiDictionaryBindings.addAll(linkRes.getSpec().getMultiDictionariesRelation());
        }
        return multiDictionaryBindings;
    }

    public ConfigMapDataContainer getConfigMapDataContainer(String key) {
        return configMapDataContainerMap.computeIfAbsent(key, k -> new ConfigMapDataContainer());
    }

    public void setLinkResources(List<Th2Link> linkResources) {
        this.linkResources = linkResources;
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
