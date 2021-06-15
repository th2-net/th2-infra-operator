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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public enum OperatorState {
    INSTANCE;

    private final Map<String, NamespaceState> namespaceStates = new ConcurrentHashMap<>();

    public void setLinkResources(String namespace, List<Th2Link> linkResources) {
        computeIfAbsent(namespace).setLinkResources(new ArrayList<>(linkResources));
    }

    public List<Th2Link> getLinkResources(String namespace) {
        var links = namespaceStates.get(namespace);
        return Objects.nonNull(links) ? Collections.unmodifiableList(links.getLinkResources()) : List.of();
    }

    public List<PinCouplingGRPC> getGrpLinks(String namespace) {
        var links = namespaceStates.get(namespace);
        return Objects.nonNull(links) ? Collections.unmodifiableList(links.getGrpcLinks()) : List.of();
    }

    public List<DictionaryBinding> getDictionaryLinks(String namespace) {
        var links = namespaceStates.get(namespace);
        return Objects.nonNull(links) ? Collections.unmodifiableList(links.getDictionaryLinks()) : List.of();
    }

    public String getConfigChecksum(String namespace, String key) {
        String checksum = namespaceStates.get(namespace).getConfigChecksums(key);
        return checksum != null ? checksum : "";
    }

    public void putConfigChecksum(String namespace, String key, String checkSum) {
        namespaceStates.get(namespace).putConfigChecksums(key, checkSum);
    }

    public NamespaceLock getLock(String namespace) {
        return computeIfAbsent(namespace);
    }

    private NamespaceState computeIfAbsent(String namespace) {
        return namespaceStates.computeIfAbsent(namespace, s -> new NamespaceState());
    }

    public HasMetadata getResourceFromCache(String name, String namespace) {
        return namespaceStates.computeIfAbsent(namespace, s -> new NamespaceState()).getResource(name);
    }

    public void putResourceInCache(HasMetadata resource, String namespace) {
        namespaceStates.computeIfAbsent(namespace, s -> new NamespaceState()).putResource(resource);
    }

    public void removeResourceFromCache(String name, String namespace) {
        namespaceStates.computeIfAbsent(namespace, s -> new NamespaceState()).removeResource(name);
    }

    public interface NamespaceLock {
        void lock();

        void unlock();
    }
}
