package com.exactpro.th2.infraoperator.spec.strategy.linkResolver;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;

import java.util.ArrayList;
import java.util.List;

public abstract class GenericLinkResolver<T> implements LinkResolver<T> {
    @Override
    public List<T> resolve(List<Th2Link> linkResources) {
        var resolvedLinks = new ArrayList<T>();

        resolve(linkResources, resolvedLinks);

        return resolvedLinks;
    }

    @Override
    public void resolve(List<Th2Link> linkResources, List<T> grpcActiveLinks) {
        resolve(linkResources, grpcActiveLinks, new Th2CustomResource[]{});
    }

    @Override
    public abstract void resolve(List<Th2Link> linkResources, List<T> grpcActiveLinks, Th2CustomResource... newResources);

    public boolean th2PinEndpointPreValidation (String namespace, String endpointName1, String endpointName2) {
        return OperatorState.INSTANCE.checkActiveTh2Resource(namespace, endpointName1)
                && OperatorState.INSTANCE.checkActiveTh2Resource(namespace, endpointName2);
    }
}
