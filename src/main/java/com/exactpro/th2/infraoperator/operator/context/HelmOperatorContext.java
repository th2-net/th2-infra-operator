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
import com.exactpro.th2.infraoperator.model.box.configuration.dictionary.factory.MultiDictionaryFactory;
import com.exactpro.th2.infraoperator.model.box.configuration.grpc.factory.GrpcRouterConfigFactory;
import com.exactpro.th2.infraoperator.model.box.configuration.mq.factory.MessageRouterConfigFactory;
import io.fabric8.kubernetes.client.KubernetesClient;

public class HelmOperatorContext {

    private final KubernetesClient client;

    public HelmOperatorContext(Builder<?, ?> builder) {
        this.client = builder.getClient();
    }

    public static ContextBuilder builder(KubernetesClient client) {
        return new ContextBuilder(client);
    }

    public KubernetesClient getClient() {
        return this.client;
    }

    public abstract static class Builder<O, T extends Builder<O, T>> {

        protected final KubernetesClient client;

        protected MessageRouterConfigFactory mqConfigFactory = new MessageRouterConfigFactory();

        protected GrpcRouterConfigFactory grpcConfigFactory = new GrpcRouterConfigFactory();

        protected DictionaryFactory dictionaryFactory = new DictionaryFactory();

        protected MultiDictionaryFactory multiDictionaryFactory = new MultiDictionaryFactory();

        public Builder(KubernetesClient client) {
            this.client = client;
        }

        public abstract O build();

        public KubernetesClient getClient() {
            return this.client;
        }

        public MessageRouterConfigFactory getMqConfigFactory() {
            return this.mqConfigFactory;
        }

        public GrpcRouterConfigFactory getGrpcConfigFactory() {
            return this.grpcConfigFactory;
        }

        public DictionaryFactory getDictionaryFactory() {
            return this.dictionaryFactory;
        }

        public MultiDictionaryFactory getMultiDictionaryFactory() {
            return this.multiDictionaryFactory;
        }
    }

    public static class ContextBuilder extends Builder<HelmOperatorContext, ContextBuilder> {

        public ContextBuilder(KubernetesClient client) {
            super(client);
        }

        @Override
        public HelmOperatorContext build() {
            return new HelmOperatorContext(this);
        }

    }

}
