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

package com.exactpro.th2.infraoperator.spec.link.validator.warnprinter.node.impl;

import com.exactpro.th2.infraoperator.spec.link.validator.warnprinter.node.WarnNode;

public abstract class AbstractIncorrectPinAttrsNode implements WarnNode {

    private Object[] args = new Object[6];

    public AbstractIncorrectPinAttrsNode(
            String pinName,
            String resNamespace,
            String resName,
            String boxDirection,
            String fromAttr,
            String toAttr
    ) {
        args[0] = pinName;
        args[1] = resNamespace;
        args[2] = resName;
        args[3] = boxDirection;
        args[4] = fromAttr;
        args[5] = toAttr;
    }

    @Override
    public String getTemplate() {
        return "The specified pin with name '{}' in box '{}.{}' has incorrect direction attributes. " +
                "Pin specified as '{}' must have the '{}' attribute and doesn't have '{}' attribute.";
    }

    @Override
    public Object[] getArgs() {
        return args;
    }

}
