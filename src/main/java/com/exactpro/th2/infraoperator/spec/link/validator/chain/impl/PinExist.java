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

package com.exactpro.th2.infraoperator.spec.link.validator.chain.impl;

import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.validator.ValidationStatus;
import com.exactpro.th2.infraoperator.spec.link.validator.chain.AbstractValidator;
import com.exactpro.th2.infraoperator.spec.link.validator.model.DirectionalLinkContext;
import com.exactpro.th2.infraoperator.spec.link.validator.warnprinter.WarnPrinter;
import com.exactpro.th2.infraoperator.spec.link.validator.warnprinter.node.impl.*;

import java.util.Objects;

public class PinExist extends AbstractValidator {

    private String pinName;

    public PinExist(String pinName) {
        this(pinName, null);
    }

    public PinExist(String pinName, WarnPrinter warnPrinter) {
        super(warnPrinter);
        this.pinName = pinName;
    }

    public PinExist(DirectionalLinkContext context) {
        this(context.getBoxPinName(), WarnPrinter.builder()
                .addNode(new PinNotFoundNode(context.getBoxPinName(), context.getLinkNamespace(), context.getBoxName()))
                .addNode(new Th2LinkResNode(context.getLinkNamespace(), context.getLinkResName()))
                .addNode(new BoxRelNode(context.getLinksSectionName()))
                .addNode(new LinkNameNode(context.getLinkName()))
                .addNode(new PinNode(context.getBoxDirection().name(), context.getBoxPinName()))
                .build());
    }

    @Override
    public ValidationStatus validate(Object object, Object... additional) {
        if (!(object instanceof Th2CustomResource)) {
            throw new IllegalStateException("Expected target of type Th2CustomResource");
        }

        var resource = (Th2CustomResource) object;

        var pin = resource.getSpec().getPin(pinName);

        if (Objects.nonNull(pin)) {
            return super.validate(pin, additional);
        }

        printWarn();

        return ValidationStatus.PIN_NOT_EXIST;
    }

}
