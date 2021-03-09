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

package com.exactpro.th2.infraoperator.spec.strategy.linkResolver.grpc.impl;

import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingGRPC;
import com.exactpro.th2.infraoperator.spec.link.validator.ValidationStatus;
import com.exactpro.th2.infraoperator.spec.link.validator.chain.impl.ExpectedPinType;
import com.exactpro.th2.infraoperator.spec.link.validator.chain.impl.PinExist;
import com.exactpro.th2.infraoperator.spec.link.validator.chain.impl.ResourceExist;
import com.exactpro.th2.infraoperator.spec.link.validator.chain.impl.StrategyExist;
import com.exactpro.th2.infraoperator.spec.link.validator.model.DirectionalLinkContext;
import com.exactpro.th2.infraoperator.spec.shared.BoxDirection;
import com.exactpro.th2.infraoperator.spec.shared.SchemaConnectionType;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.GenericLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.grpc.GrpcLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.resFinder.box.BoxResourceFinder;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public class DefaultGrpcLinkResolver extends GenericLinkResolver<PinCouplingGRPC> implements GrpcLinkResolver {

    private static final Logger logger = LoggerFactory.getLogger(DefaultGrpcLinkResolver.class);


    private final BoxResourceFinder resourceFinder;


    public DefaultGrpcLinkResolver(BoxResourceFinder resourceFinder) {
        this.resourceFinder = resourceFinder;
    }

    @Override
    public void resolve(List<Th2Link> linkResources, List<PinCouplingGRPC> grpcActiveLinks, Th2CustomResource... newResources) {

        grpcActiveLinks.clear();

        for (var lRes : linkResources) {
            for (var link : lRes.getSpec().getBoxesRelation().getRouterGrpc()) {

                if (validateLinks(lRes, link, newResources)) {
                    grpcActiveLinks.add(link);
                }

            }
        }

    }

    private boolean validateLinks(Th2Link linkRes, PinCouplingGRPC link, Th2CustomResource... additionalSource) {

        var namespace = ExtractUtils.extractNamespace(linkRes);

        if (th2PinEndpointPreValidation(namespace,
                link.getFrom().getBoxName(),
                link.getTo().getBoxName())) {
            return false;
        }

        var fromBoxSpec = link.getFrom();

        var fromBoxName = fromBoxSpec.getBoxName();

        var fromContext = DirectionalLinkContext.builder()
                .linkName(link.getName())
                .boxName(fromBoxName)
                .boxPinName(fromBoxSpec.getPinName())
                .boxDirection(BoxDirection.from)
                .linksSectionName("grpc")
                .connectionType(SchemaConnectionType.grpc)
                .routingStrategy(fromBoxSpec.getStrategy())
                .linkResName(ExtractUtils.extractName(linkRes))
                .linkNamespace(namespace)
                .build();


        var toBoxSpec = link.getTo();

        var toBoxName = toBoxSpec.getBoxName();

        var toContext = fromContext.toBuilder()
                .boxName(toBoxName)
                .boxPinName(toBoxSpec.getPinName())
                .boxDirection(BoxDirection.to)
                .routingStrategy(toBoxSpec.getStrategy())
                .build();


        var fromRes = resourceFinder.getResource(fromBoxName, namespace, additionalSource);

        var fromValRes = validateResourceByDirectionalLink(fromRes, fromContext);

        var toRes = resourceFinder.getResource(toBoxName, namespace, additionalSource);

        var toValRes = validateResourceByDirectionalLink(toRes, toContext);


        return fromValRes.equals(ValidationStatus.VALID) && toValRes.equals(ValidationStatus.VALID);
    }

    private ValidationStatus validateResourceByDirectionalLink(Th2CustomResource resource, DirectionalLinkContext context) {

        var resValidator = new ResourceExist(context);
        var pinExist = new PinExist(context);
        var expectedPin = new ExpectedPinType(context);
        var strategyExist = new StrategyExist(context);

        resValidator.setNext(pinExist);
        pinExist.setNext(expectedPin);
        expectedPin.setNext(strategyExist);

        return resValidator.validate(resource);
    }

}
