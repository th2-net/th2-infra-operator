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

import com.exactpro.th2.infraoperator.fabric8.model.box.configuration.grpc.StrategyType;
import com.exactpro.th2.infraoperator.fabric8.spec.link.validator.ValidationStatus;
import com.exactpro.th2.infraoperator.fabric8.spec.link.validator.chain.AbstractValidator;
import com.exactpro.th2.infraoperator.fabric8.spec.link.validator.model.DirectionalLinkContext;
import com.exactpro.th2.infraoperator.fabric8.spec.link.validator.warnPrinter.WarnPrinter;
import com.exactpro.th2.infraoperator.fabric8.spec.link.validator.warnPrinter.node.impl.*;

import java.util.Arrays;


public class StrategyExist extends AbstractValidator {

    private String routingStrategy;


    public StrategyExist(String routingStrategy) {
        this.routingStrategy = routingStrategy;
    }

    public StrategyExist(String routingStrategy, WarnPrinter warnPrinter) {
        super(warnPrinter);
        this.routingStrategy = routingStrategy;
    }

    public StrategyExist(DirectionalLinkContext context) {
        this(context.getRoutingStrategy(), WarnPrinter.builder()
                .addNode(new StrategyNotFoundNode(context.getRoutingStrategy()))
                .addNode(new Th2LinkResNode(context.getLinkNamespace(), context.getLinkResName()))
                .addNode(new BoxRelNode(context.getLinksSectionName()))
                .addNode(new LinkNameNode(context.getLinkName()))
                .addNode(new StrategyNode(context.getBoxDirection().name(), context.getRoutingStrategy()))
                .build());
    }


    @Override
    public ValidationStatus validate(Object object, Object... additional) {

        var isStrategyExist = Arrays.stream(StrategyType.values())
                .map(StrategyType::getActualName)
                .anyMatch(n -> n.equals(routingStrategy));

        if (isStrategyExist) {
            return super.validate(object, additional);
        }

        printWarn();

        return ValidationStatus.STRATEGY_NOT_EXIST;

    }

}
