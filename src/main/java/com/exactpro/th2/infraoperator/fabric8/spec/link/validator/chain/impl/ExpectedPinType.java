/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.infraoperator.fabric8.spec.link.validator.chain.impl;

import com.exactpro.th2.infraoperator.fabric8.spec.link.validator.ValidationStatus;
import com.exactpro.th2.infraoperator.fabric8.spec.link.validator.chain.AbstractValidator;
import com.exactpro.th2.infraoperator.fabric8.spec.link.validator.model.DirectionalLinkContext;
import com.exactpro.th2.infraoperator.fabric8.spec.link.validator.warnPrinter.WarnPrinter;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.PinSpec;
import com.exactpro.th2.infraoperator.fabric8.spec.shared.SchemaConnectionType;
import com.exactpro.th2.infraoperator.fabric8.spec.link.validator.warnPrinter.node.impl.*;


public class ExpectedPinType extends AbstractValidator {

    private SchemaConnectionType connectionType;


    public ExpectedPinType(SchemaConnectionType connectionType) {
        this(connectionType, null);
    }

    public ExpectedPinType(SchemaConnectionType connectionType, WarnPrinter warnPrinter) {
        super(warnPrinter);
        this.connectionType = connectionType;
    }

    public ExpectedPinType(DirectionalLinkContext context) {
        this(context.getConnectionType(), WarnPrinter.builder()
                .addNode(new IncorrectPinTypeNode(context.getBoxPinName(), context.getLinkNamespace(), context.getBoxName(), context.getConnectionType()))
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

        if (pin.getConnectionType().equals(connectionType)) {
            return super.validate(pin, additional);
        }

        printWarn();

        return ValidationStatus.PIN_NOT_MQ;

    }

}
