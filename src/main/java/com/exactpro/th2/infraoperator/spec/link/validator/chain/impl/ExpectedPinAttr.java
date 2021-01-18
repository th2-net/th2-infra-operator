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

import com.exactpro.th2.infraoperator.spec.link.validator.ValidationStatus;
import com.exactpro.th2.infraoperator.spec.link.validator.chain.AbstractValidator;
import com.exactpro.th2.infraoperator.spec.link.validator.model.DirectionalLinkContext;
import com.exactpro.th2.infraoperator.spec.link.validator.warnPrinter.WarnPrinter;
import com.exactpro.th2.infraoperator.spec.link.validator.warnPrinter.node.impl.*;
import com.exactpro.th2.infraoperator.spec.shared.BoxDirection;
import com.exactpro.th2.infraoperator.spec.shared.PinAttribute;
import com.exactpro.th2.infraoperator.spec.shared.PinSpec;


public class ExpectedPinAttr extends AbstractValidator {

    private String linkResName;

    private BoxDirection boxDirection;


    public ExpectedPinAttr(BoxDirection boxDirection, String linkResName) {
        this(boxDirection, linkResName, null);
    }

    public ExpectedPinAttr(BoxDirection boxDirection, String linkResName, WarnPrinter warnPrinter) {
        super(warnPrinter);
        this.linkResName = linkResName;
        this.boxDirection = boxDirection;
    }

    public ExpectedPinAttr(DirectionalLinkContext context) {
        this(context.getBoxDirection(), context.getLinkResName(), WarnPrinter.builder()
                .addNode(
                        context.getBoxDirection().equals(BoxDirection.to)
                                ? new IncorrectToPinAttrsNode(context.getBoxPinName(), context.getLinkNamespace(), context.getBoxName())
                                : new IncorrectFromPinAttrsNode(context.getBoxPinName(), context.getLinkNamespace(), context.getBoxName())
                )
                .addNode(new Th2LinkResNode(context.getLinkNamespace(), context.getLinkResName()))
                .addNode(new BoxRelNode(context.getLinksSectionName()))
                .addNode(new LinkNameNode(context.getLinkName()))
                .addNode(new PinNode(context.getBoxDirection().name(), context.getBoxPinName()))
                .build());

    }


    @Override
    public ValidationStatus validate(Object object, Object... additional) {
        if (!(object instanceof PinSpec)) {
            throw new IllegalStateException("Expected target of type PinSpec");
        }


        var pin = (PinSpec) object;

        switch (boxDirection) {
            case to:

                if (pin.getAttributes().contains(PinAttribute.publish.name())) {
                    printError();
                    return ValidationStatus.INVALID_PIN_DIRECTION_ATTR;
                } else if (!pin.getAttributes().contains(PinAttribute.subscribe.name())) {
                    printWarn();
                }

                break;

            case from:

                if (!pin.getAttributes().contains(PinAttribute.publish.name())
                        || pin.getAttributes().contains(PinAttribute.subscribe.name())) {
                    printError();
                    return ValidationStatus.INVALID_PIN_DIRECTION_ATTR;
                }

                break;
        }

        return super.validate(pin, additional);

    }

}
