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

package com.exactpro.th2.infraoperator.spec.strategy.resFinder.box.impl;

import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.Th2Spec;
import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.spec.shared.PinSpec;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.box.BoxResourceFinder;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import com.exactpro.th2.infraoperator.spec.shared.DirectionAttribute;
import com.exactpro.th2.infraoperator.spec.shared.SchemaConnectionType;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.exactpro.th2.infraoperator.operator.StoreHelmTh2Op.EVENT_STORAGE_PIN_ALIAS;

public class StoreDependentBoxResourceFinder implements BoxResourceFinder {

    private BoxResourceFinder resourceFinder;


    public StoreDependentBoxResourceFinder(BoxResourceFinder resourceFinder) {
        this.resourceFinder = resourceFinder;
    }


    @Nullable
    @Override
    public Th2CustomResource getResource(String name, String namespace, Th2CustomResource... additionalSource) {

        var resource = resourceFinder.getResource(name, namespace, additionalSource);

        if (ExtractUtils.isStorageBox(name)) {

            synchronized (OperatorState.INSTANCE.getLock(namespace)) {

                var stPins = generateStoragePins(namespace);

                if (Objects.nonNull(resource)) {
                    resource.getSpec().setPins(stPins);
                    return resource;
                }

                return generateFakeResource(name, namespace, stPins);

            }

        }

        return resource;
    }

    @Override
    public List<Th2CustomResource> getResources() {
        return resourceFinder.getResources();
    }

    @Override
    public List<Th2CustomResource> getResources(String namespace) {
        return resourceFinder.getResources(namespace);
    }


    private List<PinSpec> generateStoragePins(String namespace) {
        return OperatorState.INSTANCE.getLinkResources(namespace).stream()
                .filter(ExtractUtils::isStorageResource)
                .flatMap(lr -> lr.getSpec().getBoxesRelation().getRouterMq().stream())
                .map(l -> {
                    var toPinName = l.getTo().getPinName();

                    var pin = new PinSpec();
                    pin.setName(toPinName);
                    pin.setConnectionType(SchemaConnectionType.mq);

                    var attributes = new HashSet<>(Set.of(DirectionAttribute.subscribe.name()));

                    if (toPinName.contains(DirectionAttribute.parsed.name())) {
                        attributes.add(DirectionAttribute.parsed.name());
                    } else if (toPinName.contains(DirectionAttribute.raw.name())) {
                        attributes.add(DirectionAttribute.raw.name());
                    } else if (toPinName.equals(EVENT_STORAGE_PIN_ALIAS)) {
                        attributes.add(DirectionAttribute.event.name());
                    }

                    pin.setAttributes(attributes);

                    return pin;
                }).collect(Collectors.toList());
    }

    private Th2CustomResource generateFakeResource(String name, String namespace, List<PinSpec> stPins) {
        return new Th2CustomResource() {
            @Override
            public Th2Spec getSpec() {
                return new Th2Spec() {
                    @Override
                    public List<PinSpec> getPins() {
                        return stPins;
                    }

                };
            }

            @Override
            public ObjectMeta getMetadata() {
                return new ObjectMeta() {
                    @Override
                    public String getName() {
                        return name;
                    }

                    @Override
                    public String getNamespace() {
                        return namespace;
                    }
                };
            }
        };
    }

}

