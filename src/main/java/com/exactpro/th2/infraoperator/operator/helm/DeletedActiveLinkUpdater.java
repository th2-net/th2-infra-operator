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

import com.exactpro.th2.infraoperator.model.box.schema.link.EnqueuedLink;
import com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.DictionaryBinding;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCoupling;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingGRPC;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.dictionary.DictionaryLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.grpc.GrpcLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.QueueLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.impl.DeclareQueueResolver;

import java.util.List;

public class DeletedActiveLinkUpdater extends ActiveLinkUpdater {

    private final HelmReleaseTh2Op helmReleaseTh2Op;

    private final GrpcLinkResolver grpcLinkResolver;

    private final QueueLinkResolver queueGenLinkResolver;

    private final DictionaryLinkResolver dictionaryLinkResolver;

    private final DeclareQueueResolver declareQueueResolver;

    public DeletedActiveLinkUpdater(HelmReleaseTh2Op helmReleaseTh2Op, GrpcLinkResolver grpcLinkResolver,
                                    QueueLinkResolver queueGenLinkResolver,
                                    DictionaryLinkResolver dictionaryLinkResolver,
                                    DeclareQueueResolver declareQueueResolver) {
        this.helmReleaseTh2Op = helmReleaseTh2Op;
        this.grpcLinkResolver = grpcLinkResolver;
        this.queueGenLinkResolver = queueGenLinkResolver;
        this.dictionaryLinkResolver = dictionaryLinkResolver;
        this.declareQueueResolver = declareQueueResolver;
    }

    @Override
    protected void refreshGrpcLinks(List<Th2Link> linkResources, List<PinCouplingGRPC> grpcActiveLinks,
                                    Th2CustomResource... newResources) {
        grpcLinkResolver.resolve(linkResources, grpcActiveLinks);
    }

    @Override
    protected void refreshMqLinks(List<Th2Link> linkResources, List<EnqueuedLink> mqActiveLinks,
                                  Th2CustomResource... newResources) {
        queueGenLinkResolver.resolve(linkResources, mqActiveLinks);
    }

    @Override
    protected void refreshDictionaryLinks(List<Th2Link> linkResources, List<DictionaryBinding> dicActiveLinks,
                                          Th2CustomResource... newResources) {
        dictionaryLinkResolver.resolve(linkResources, dicActiveLinks);
    }

    @Override
    protected List<Th2CustomResource> getLinkedResources(Th2CustomResource resource, List<PinCoupling> oldLinks,
                                                         List<PinCoupling> newLinks) {
        return helmReleaseTh2Op.getAllLinkedResources(resource, oldLinks);
    }

    @Override
    protected void refreshQueues(Th2CustomResource resource) {
        declareQueueResolver.resolveDelete(resource);
    }
}
