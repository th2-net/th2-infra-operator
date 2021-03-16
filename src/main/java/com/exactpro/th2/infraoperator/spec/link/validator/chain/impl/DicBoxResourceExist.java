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

import com.exactpro.th2.infraoperator.spec.link.validator.model.DirectionalLinkContext;
import com.exactpro.th2.infraoperator.spec.link.validator.warnprinter.WarnPrinter;
import com.exactpro.th2.infraoperator.spec.link.validator.warnprinter.node.impl.*;

public class DicBoxResourceExist extends ResourceExist {

    public DicBoxResourceExist() {
    }

    public DicBoxResourceExist(WarnPrinter warnPrinter) {
        super(warnPrinter);
    }

    public DicBoxResourceExist(DirectionalLinkContext context) {
        this(WarnPrinter.builder()
                .addNode(new ResNotFoundNode(context.getBoxName(), context.getLinkNamespace()))
                .addNode(new Th2LinkResNode(context.getLinkNamespace(), context.getLinkResName()))
                .addNode(new DicRelNode())
                .addNode(new LinkNameNode(context.getLinkName()))
                .addNode(new DicBoxNameNode(context.getBoxName()))
                .build());
    }

}
