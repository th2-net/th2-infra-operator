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

import com.exactpro.th2.infraoperator.model.box.schema.link.EnqueuedLink;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.DictionaryBinding;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCoupling;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingGRPC;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.EVENT_STORAGE_BOX_ALIAS;
import static com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.MESSAGE_STORAGE_BOX_ALIAS;

public enum OperatorState {
    INSTANCE;

    private final Map<String, NamespaceState> namespaceStates = new ConcurrentHashMap<>();

    public void setLinkResources(String namespace, List<Th2Link> linkResources) {
        computeIfAbsent(namespace).setLinkResources(new ArrayList<>(linkResources));
    }

    public void setMqActiveLinks(String namespace, List<EnqueuedLink> activeLinks) {
        computeIfAbsent(namespace).setMqActiveLinks(new ArrayList<>(activeLinks));
    }

    public void setGrpcActiveLinks(String namespace, List<PinCouplingGRPC> activeLinks) {
        computeIfAbsent(namespace).setGrpcActiveLinks(new ArrayList<>(activeLinks));
    }

    public void setDictionaryActiveLinks(String namespace, List<DictionaryBinding> activeLinks) {
        computeIfAbsent(namespace).setDictionaryBindings(new ArrayList<>(activeLinks));
    }

    public List<Th2Link> getLinkResources(String namespace) {
        var links = namespaceStates.get(namespace);
        return Objects.nonNull(links) ? Collections.unmodifiableList(links.getLinkResources()) : List.of();
    }

    public List<PinCoupling> getAllBoxesActiveLinks(String namespace) {
        var links = namespaceStates.get(namespace);
        if (Objects.isNull(links)) {
            return List.of();
        }
        List<PinCoupling> allLinks = new ArrayList<>(links.getMqActiveLinks());
        allLinks.addAll(links.getGrpcActiveLinks());
        return Collections.unmodifiableList(allLinks);
    }

    public List<EnqueuedLink> getMqActiveLinks(String namespace) {
        var links = namespaceStates.get(namespace);
        return Objects.nonNull(links) ? Collections.unmodifiableList(links.getMqActiveLinks()) : List.of();
    }

    public List<PinCouplingGRPC> getGrpcActiveLinks(String namespace) {
        var links = namespaceStates.get(namespace);
        return Objects.nonNull(links) ? Collections.unmodifiableList(links.getGrpcActiveLinks()) : List.of();
    }

    public List<DictionaryBinding> getDictionaryActiveLinks(String namespace) {
        var links = namespaceStates.get(namespace);
        return Objects.nonNull(links) ? Collections.unmodifiableList(links.getDictionaryBindings()) : List.of();
    }

    public List<EnqueuedLink> getMsgStorageActiveLinks(String namespace) {
        return getMqActiveLinks(namespace).stream()
                .filter(queueLinkBunch -> queueLinkBunch.getTo().getBoxName().equals(MESSAGE_STORAGE_BOX_ALIAS))
                .collect(Collectors.toUnmodifiableList());
    }

    public List<EnqueuedLink> getEventStorageActiveLinks(String namespace) {
        return getMqActiveLinks(namespace).stream()
                .filter(queueLinkBunch -> queueLinkBunch.getTo().getBoxName().equals(EVENT_STORAGE_BOX_ALIAS))
                .collect(Collectors.toUnmodifiableList());
    }

    public List<EnqueuedLink> getGeneralMqActiveLinks(String namespace) {
        return getMqActiveLinks(namespace).stream()
                .filter(queueLinkBunch -> !queueLinkBunch.getTo().getBoxName().equals(MESSAGE_STORAGE_BOX_ALIAS)
                        && !queueLinkBunch.getTo().getBoxName().equals(EVENT_STORAGE_BOX_ALIAS))
                .collect(Collectors.toUnmodifiableList());
    }

    public NamespaceLock getLock(String namespace) {
        return computeIfAbsent(namespace);
    }

    private NamespaceState computeIfAbsent(String namespace) {
        return namespaceStates.computeIfAbsent(namespace, s -> new NamespaceState(namespace));
    }

    public void addActiveTh2Resource(String namespace, String resourceName) {
        computeIfAbsent(namespace).availableTh2Resources.add(resourceName);
    }

    public void removeActiveResource(String namespace, String resourceName) {
        computeIfAbsent(namespace).availableTh2Resources.remove(resourceName);
    }

    public boolean checkActiveTh2Resource(String namespace, String resourceName) {
        return computeIfAbsent(namespace).availableTh2Resources.contains(resourceName);
    }

    public interface NamespaceLock {
        void lock();

        void unlock();
    }

    public static class NamespaceState implements NamespaceLock {

        private String name;

        private final Set<String> availableTh2Resources;

        private List<Th2Link> linkResources = new ArrayList<>();

        private List<EnqueuedLink> mqActiveLinks = new ArrayList<>();

        private List<PinCouplingGRPC> grpcActiveLinks = new ArrayList<>();

        private List<DictionaryBinding> dictionaryBindings = new ArrayList<>();

        private final Lock lock = new ReentrantLock(true);

        public NamespaceState(String name) {
            this.name = name;
            this.availableTh2Resources = new HashSet<>();
        }

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

        public List<EnqueuedLink> getMqActiveLinks() {
            return this.mqActiveLinks;
        }

        public List<PinCouplingGRPC> getGrpcActiveLinks() {
            return this.grpcActiveLinks;
        }

        public List<DictionaryBinding> getDictionaryBindings() {
            return this.dictionaryBindings;
        }

        public void setLinkResources(List<Th2Link> linkResources) {
            this.linkResources = linkResources;
        }

        public void setMqActiveLinks(List<EnqueuedLink> mqActiveLinks) {
            this.mqActiveLinks = mqActiveLinks;
        }

        public void setGrpcActiveLinks(List<PinCouplingGRPC> grpcActiveLinks) {
            this.grpcActiveLinks = grpcActiveLinks;
        }

        public void setDictionaryBindings(List<DictionaryBinding> dictionaryBindings) {
            this.dictionaryBindings = dictionaryBindings;
        }

        @Override
        public boolean equals(final Object o) {
            throw new AssertionError("method not supported");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("method not supported");
        }
    }

}
