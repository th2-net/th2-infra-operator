package com.exactpro.th2.infraoperator.operator;

import com.exactpro.th2.infraoperator.spec.shared.PinAttribute;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.Set;

@Getter
@SuperBuilder
class MsgStorageContext extends StorageContext {

    @Override
    protected boolean checkAttributes(Set<String> attributes) {
        return attributes.contains(PinAttribute.store.name())
                && attributes.contains(PinAttribute.publish.name())
                && (attributes.contains(PinAttribute.parsed.name())
                || attributes.contains(PinAttribute.raw.name()))
                && !attributes.contains(PinAttribute.subscribe.name());
    }

}
