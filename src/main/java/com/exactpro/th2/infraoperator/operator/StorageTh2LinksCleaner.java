package com.exactpro.th2.infraoperator.operator;

import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingMQ;

import java.util.ArrayList;
import java.util.List;

class StorageTh2LinksCleaner<CR extends Th2CustomResource> extends StorageTh2LinksRefresher<CR> {

    public StorageTh2LinksCleaner(StorageContext context) {
        super(context);
    }

    @Override
    protected List<PinCouplingMQ> update(List<PinCouplingMQ> oldHiddenLinks, List<PinCouplingMQ> newHiddenLinks) {
        List<PinCouplingMQ> updated = new ArrayList<>(oldHiddenLinks);
        updated.removeAll(newHiddenLinks);
        return updated;
    }

}
