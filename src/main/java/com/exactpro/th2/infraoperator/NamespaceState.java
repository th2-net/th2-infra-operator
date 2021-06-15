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

import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.DictionaryBinding;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingGRPC;
import io.fabric8.kubernetes.api.model.HasMetadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class NamespaceState implements OperatorState.NamespaceLock {
    private List<Th2Link> linkResources = new ArrayList<>();

    private Map<String, String> configChecksums = new HashMap<>();

    private final Map<String, HasMetadata> resources = new HashMap<>();

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

    public String getConfigChecksums(String key) {
        return configChecksums.get(key);
    }

    public void setLinkResources(List<Th2Link> linkResources) {
        this.linkResources = linkResources;
    }

    public void putConfigChecksums(String key, String configChecksum) {
        this.configChecksums.put(key, configChecksum);
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

    @Override
    public boolean equals(final Object o) {
        throw new AssertionError("method not supported");
    }

    @Override
    public int hashCode() {
        throw new AssertionError("method not supported");
    }

    static final class ResourceCache {
    }
}
