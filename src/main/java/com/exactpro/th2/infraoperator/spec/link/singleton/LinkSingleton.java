/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.infraoperator.spec.link.singleton;

import com.exactpro.th2.infraoperator.model.box.schema.link.EnqueuedLink;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCoupling;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingGRPC;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.DictionaryBinding;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.EVENT_STORAGE_BOX_ALIAS;
import static com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.MESSAGE_STORAGE_BOX_ALIAS;


public enum LinkSingleton {

    INSTANCE;


    private Map<String, Object> lockPerNamespace = new ConcurrentHashMap<>();

    private Map<String, ClusterLinks> linksPerNamespace = new ConcurrentHashMap<>();


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
        computeIfAbsent(namespace).setDictionaryActiveLinks(new ArrayList<>(activeLinks));
    }


    public List<Th2Link> getLinkResources(String namespace) {
        var links = linksPerNamespace.get(namespace);
        return Objects.nonNull(links) ? Collections.unmodifiableList(links.getLinkResources()) : List.of();
    }

    public List<PinCoupling> getAllBoxesActiveLinks(String namespace) {
        var links = linksPerNamespace.get(namespace);
        if (Objects.isNull(links)) {
            return List.of();
        }
        List<PinCoupling> allLinks = new ArrayList<>(links.getMqActiveLinks());
        allLinks.addAll(links.getGrpcActiveLinks());
        return Collections.unmodifiableList(allLinks);
    }

    public List<EnqueuedLink> getMqActiveLinks(String namespace) {
        var links = linksPerNamespace.get(namespace);
        return Objects.nonNull(links) ? Collections.unmodifiableList(links.getMqActiveLinks()) : List.of();
    }

    public List<PinCouplingGRPC> getGrpcActiveLinks(String namespace) {
        var links = linksPerNamespace.get(namespace);
        return Objects.nonNull(links) ? Collections.unmodifiableList(links.getGrpcActiveLinks()) : List.of();
    }

    public List<DictionaryBinding> getDictionaryActiveLinks(String namespace) {
        var links = linksPerNamespace.get(namespace);
        return Objects.nonNull(links) ? Collections.unmodifiableList(links.getDictionaryActiveLinks()) : List.of();
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

    public Object getLock(String namespace) {
        return lockPerNamespace.computeIfAbsent(namespace, n -> new Object());
    }


    private ClusterLinks computeIfAbsent(String namespace) {
        return linksPerNamespace.computeIfAbsent(namespace, s -> new ClusterLinks());
    }

}
