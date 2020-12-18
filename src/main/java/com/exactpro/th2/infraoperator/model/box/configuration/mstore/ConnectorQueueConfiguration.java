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

package com.exactpro.th2.infraoperator.model.box.configuration.mstore;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonDeserialize
public class ConnectorQueueConfiguration {

    private String exchangeName;
    private String inQueueName;
    private String inRawQueueName;
    private String outQueueName;
    private String outRawQueueName;


    protected ConnectorQueueConfiguration() {
    }

    protected ConnectorQueueConfiguration(String exchangeName, String inQueueName, String inRawQueueName, String outQueueName, String outRawQueueName) {
        this.exchangeName = exchangeName;
        this.inQueueName = inQueueName;
        this.inRawQueueName = inRawQueueName;
        this.outQueueName = outQueueName;
        this.outRawQueueName = outRawQueueName;
    }
}
