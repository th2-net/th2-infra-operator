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

import com.exactpro.th2.infraoperator.spec.shared.PinAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class MsgStorageContext extends StorageContext {
    private static final Logger logger = LoggerFactory.getLogger(MsgStorageContext.class);

    public MsgStorageContext(String linkResourceName, String linkNameSuffix,
                                String boxAlias, String pinName) {
        super(linkResourceName, linkNameSuffix, boxAlias, pinName);
    }

    @Override
    public boolean checkAttributes(Set<String> attributes, String pinAnnotation) {
        if (attributes.contains(PinAttribute.store.name())
                && attributes.contains(PinAttribute.publish.name())
                && !attributes.contains(PinAttribute.subscribe.name())) {
            if (attributes.contains(PinAttribute.raw.name())
                    && !attributes.contains(PinAttribute.parsed.name())) {
                return true;
            }
            logger.warn("Pin \"{}\" is invalid. attribute: \"{}\" is not compatible with attribute: \"{}\"",
                    pinAnnotation, PinAttribute.parsed.name(), PinAttribute.store.name());
        }
        return false;
    }
}
