/*
 * Copyright 2022 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.infraoperator.spec.shared.pin;

import java.util.ArrayList;
import java.util.List;

public class PinSpec {
    private List<MqPin> mq = new ArrayList<>();

    private GrpcSection grpc = new GrpcSection();

    public List<MqPin> getMq() {
        return mq;
    }

    public GrpcSection getGrpc() {
        return grpc;
    }

    public MqPin getMqPin(String name) {
        return this.mq.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public GrpcClientPin getGrpcClientPin(String name) {
        return this.grpc.getClient().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public GrpcServerPin getGrpcServerPin(String name) {
        return this.grpc.getServer().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public void setMq(List<MqPin> mq) {
        this.mq = mq;
    }

    public void setGrpcClient(List<GrpcClientPin> client) {
        this.grpc.setClient(client);
    }

    public void setGrpcServer(List<GrpcServerPin> server) {
        this.grpc.setServer(server);
    }

}
