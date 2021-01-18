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

package com.exactpro.th2.infraoperator.spec.link.validator.warnPrinter;

import com.exactpro.th2.infraoperator.spec.link.validator.warnPrinter.node.WarnNode;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@Getter
public class WarnPrinter {

    private static final Logger logger = LoggerFactory.getLogger(WarnPrinter.class);


    private final String template;

    private final Object[] args;


    public WarnPrinter(Builder builder) {
        this.template = builder.warnMessage + String.join("->", builder.warnNodes);
        this.args = builder.warnArgs.toArray();
    }


    public void printWarn() {
        logger.warn(template, args);
    }

    public void printError() {
        logger.error(template, args);
    }


    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String warnMessage = "";
        private List<Object> warnArgs = new ArrayList<>();
        private List<String> warnNodes = new ArrayList<>();


        public Builder addNode(WarnNode node) {
            if(warnMessage.isEmpty()){
                warnMessage = node.getTemplate();
            }else {
                warnNodes.add(node.getTemplate());
            }

            warnArgs.addAll(Arrays.asList(node.getArgs()));

            return this;
        }

        public WarnPrinter build() {
            return new WarnPrinter(this);
        }
    }

}
