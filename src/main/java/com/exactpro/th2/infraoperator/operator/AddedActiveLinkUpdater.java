package com.exactpro.th2.infraoperator.operator;

import com.exactpro.th2.infraoperator.model.box.schema.link.EnqueuedLink;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.DictionaryBinding;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCoupling;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingGRPC;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.dictionary.DictionaryLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.grpc.GrpcLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.mq.QueueLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.mq.impl.DeclareQueueResolver;

import java.util.List;

class AddedActiveLinkUpdater extends ActiveLinkUpdater {

    private final HelmReleaseTh2Op helmReleaseTh2Op;
    private final GrpcLinkResolver grpcLinkResolver;
    private final QueueLinkResolver queueGenLinkResolver;
    private final DictionaryLinkResolver dictionaryLinkResolver;
    private final DeclareQueueResolver declareQueueResolver;

    public AddedActiveLinkUpdater(HelmReleaseTh2Op helmReleaseTh2Op, GrpcLinkResolver grpcLinkResolver,
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
        grpcLinkResolver.resolve(linkResources, grpcActiveLinks, newResources);
    }

    @Override
    protected void refreshMqLinks(List<Th2Link> linkResources, List<EnqueuedLink> mqActiveLinks,
                                  Th2CustomResource... newResources) {
        queueGenLinkResolver.resolve(linkResources, mqActiveLinks, newResources);
    }

    @Override
    protected void refreshDictionaryLinks(List<Th2Link> linkResources, List<DictionaryBinding> dicActiveLinks,
                                          Th2CustomResource... newResources) {
        dictionaryLinkResolver.resolve(linkResources, dicActiveLinks, newResources);
    }

    @Override
    protected List<Th2CustomResource> getLinkedResources(Th2CustomResource resource, List<PinCoupling> oldLinks,
                                                         List<PinCoupling> newLinks) {
        return helmReleaseTh2Op.getAllLinkedResources(resource, newLinks);
    }

    @Override
    protected void refreshQueues(Th2CustomResource resource) {
        declareQueueResolver.resolveAdd(resource);
    }
}
