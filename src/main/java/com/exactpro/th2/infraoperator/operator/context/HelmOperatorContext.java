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

package com.exactpro.th2.infraoperator.operator.context;

import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.factory.DictionaryFactory;
import com.exactpro.th2.infraoperator.model.box.configuration.grpc.factory.GrpcRouterConfigFactory;
import com.exactpro.th2.infraoperator.model.box.configuration.mq.factory.MessageRouterConfigFactory;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.dictionary.DictionaryLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.grpc.GrpcLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.QueueLinkResolver;
import com.exactpro.th2.infraoperator.spec.strategy.resfinder.box.BoxResourceFinder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Getter;

@Getter
public class HelmOperatorContext {

    private final KubernetesClient client;

    private final BoxResourceFinder resourceFinder;

    private final GrpcLinkResolver grpcLinkResolver;

    private final DictionaryLinkResolver dictionaryLinkResolver;

    private final QueueLinkResolver queueGenLinkResolver;

    private final MessageRouterConfigFactory mqConfigFactory;

    private final GrpcRouterConfigFactory grpcConfigFactory;

    private final DictionaryFactory dictionaryFactory;

    public HelmOperatorContext(Builder<?, ?> builder) {
        this.client = builder.getClient();
        this.resourceFinder = builder.getResourceFinder();
        this.mqConfigFactory = builder.getMqConfigFactory();
        this.grpcLinkResolver = builder.getGrpcLinkResolver();
        this.dictionaryLinkResolver = builder.getDictionaryLinkResolver();
        this.queueGenLinkResolver = builder.getQueueGenLinkResolver();
        this.grpcConfigFactory = builder.getGrpcConfigFactory();
        this.dictionaryFactory = builder.getDictionaryFactory();
    }

    public static ContextBuilder builder(KubernetesClient client) {
        return new ContextBuilder(client);
    }

    @Getter
    public abstract static class Builder<O, T extends Builder<O, T>> {

        protected final KubernetesClient client;

        protected BoxResourceFinder resourceFinder = null;

        protected GrpcLinkResolver grpcLinkResolver = null;

        protected DictionaryLinkResolver dictionaryLinkResolver = null;

        protected QueueLinkResolver queueGenLinkResolver = null;

        protected MessageRouterConfigFactory mqConfigFactory = new MessageRouterConfigFactory();

        protected GrpcRouterConfigFactory grpcConfigFactory = null;

        protected DictionaryFactory dictionaryFactory = null;

        public Builder(KubernetesClient client) {
            this.client = client;
        }

        public T resourceFinder(BoxResourceFinder resourceFinder) {
            this.resourceFinder = resourceFinder;
            return self();
        }

        public T grpcLinkResolver(GrpcLinkResolver grpcLinkResolver) {
            this.grpcLinkResolver = grpcLinkResolver;
            return self();
        }

        public T dictionaryLinkResolver(DictionaryLinkResolver dictionaryLinkResolver) {
            this.dictionaryLinkResolver = dictionaryLinkResolver;
            return self();
        }

        public T queueGenLinkResolver(QueueLinkResolver queueGenLinkResolver) {
            this.queueGenLinkResolver = queueGenLinkResolver;
            return self();
        }

        public T mqConfigFactory(MessageRouterConfigFactory mqConfigFactory) {
            this.mqConfigFactory = mqConfigFactory;
            return self();
        }

        public T grpcConfigFactory(GrpcRouterConfigFactory grpcConfigFactory) {
            this.grpcConfigFactory = grpcConfigFactory;
            return self();
        }

        public T dictionaryFactory(DictionaryFactory dictionaryFactory) {
            this.dictionaryFactory = dictionaryFactory;
            return self();
        }

        public abstract O build();

        protected abstract T self();
    }

    public static class ContextBuilder extends Builder<HelmOperatorContext, ContextBuilder> {

        public ContextBuilder(KubernetesClient client) {
            super(client);
        }

        @Override
        protected ContextBuilder self() {
            return this;
        }

        @Override
        public HelmOperatorContext build() {
            return new HelmOperatorContext(this);
        }

    }

}
