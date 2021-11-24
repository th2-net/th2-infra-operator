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

package com.exactpro.th2.infraoperator.operator.helm;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingMQ;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinMQ;
import com.exactpro.th2.infraoperator.spec.shared.PinAttribute;
import com.exactpro.th2.infraoperator.spec.shared.PinSpec;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.BindQueueLinkResolver;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class StorageTh2LinksRefresher<CR extends Th2CustomResource> {
    private static final Logger logger = LoggerFactory.getLogger(StorageTh2LinksRefresher.class);

    private StorageContext context;

    public StorageTh2LinksRefresher(StorageContext context) {
        this.context = context;
    }

    public void updateStorageResLinks(CR resource) {

        String resName = ExtractUtils.extractName(resource);

        String resNamespace = ExtractUtils.extractNamespace(resource);

        OperatorState lSingleton = OperatorState.INSTANCE;

        ArrayList<Th2Link> linkResources = new ArrayList<>(lSingleton.getLinkResources(resNamespace));

        Th2Link hiddenLinksRes = getStLinkResAndCreateIfAbsent(resNamespace, linkResources);

        List<PinCouplingMQ> oldHiddenLinks = hiddenLinksRes.getSpec().getBoxesRelation().getRouterMq();

        List<PinCouplingMQ> newHiddenLinks = createHiddenLinks(resource);

        List<PinCouplingMQ> updatedHiddenLinks = update(oldHiddenLinks, newHiddenLinks, resName);

        hiddenLinksRes.getSpec().getBoxesRelation().setRouterMq(updatedHiddenLinks);

        OperatorState.INSTANCE.setLinkResources(resNamespace, linkResources);

        BindQueueLinkResolver.removeExtinctBindings(resNamespace, oldHiddenLinks, updatedHiddenLinks);

        logger.info("{} hidden links has been refreshed successfully with '{}.{}'", context.getBoxAlias(),
                resNamespace, resName);

    }

    @SneakyThrows
    protected Th2Link getStLinkResAndCreateIfAbsent(String namespace, List<Th2Link> linkResources) {

        var linkResourceName = context.getLinkResourceName();

        var hiddenLinksRes = linkResources.stream()
                .filter(lr -> ExtractUtils.extractName(lr).equals(linkResourceName))
                .findFirst()
                .orElse(null);

        if (Objects.isNull(hiddenLinksRes)) {
            hiddenLinksRes = Th2Link.newInstance();

            var hlMetadata = hiddenLinksRes.getMetadata();

            hlMetadata.setName(linkResourceName);
            hlMetadata.setNamespace(namespace);

            linkResources.add(hiddenLinksRes);
        }

        return hiddenLinksRes;
    }

    protected PinMQ createToBoxOfHiddenLink(PinSpec pin) {
        var hyphen = "-";
        var targetAttr = "";

        if (pin.getAttributes().contains(PinAttribute.parsed.name())) {
            targetAttr += hyphen + PinAttribute.parsed.name();
        } else if (pin.getAttributes().contains(PinAttribute.raw.name())) {
            targetAttr += hyphen + PinAttribute.raw.name();
        }

        return new PinMQ(context.getBoxAlias(), context.getPinName() + targetAttr);
    }

    protected PinCouplingMQ createHiddenLink(PinMQ fromBox, PinMQ toBox) {
        return new PinCouplingMQ(fromBox.toString() + context.getLinkNameSuffix(), fromBox, toBox);
    }

    protected List<PinCouplingMQ> createHiddenLinks(Th2CustomResource resource) {

        List<PinCouplingMQ> links = new ArrayList<>();

        for (var pin : resource.getSpec().getPins()) {

            if (context.checkAttributes(pin.getAttributes())) {

                var fromLink = new PinMQ(ExtractUtils.extractName(resource), pin.getName());

                var toLink = createToBoxOfHiddenLink(pin);

                links.add(createHiddenLink(fromLink, toLink));
            }
        }

        return links;
    }

    protected abstract List<PinCouplingMQ> update(List<PinCouplingMQ> oldHiddenLinks,
                                                  List<PinCouplingMQ> newHiddenLinks,
                                                  String resName);
}
