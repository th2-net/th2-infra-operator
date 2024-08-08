/*
 * Copyright 2020-2024 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.infraoperator;

import com.exactpro.th2.infraoperator.metrics.OperatorMetrics;
import com.exactpro.th2.infraoperator.metrics.PrometheusServer;
import com.exactpro.th2.infraoperator.operator.impl.BoxHelmTh2Op;
import com.exactpro.th2.infraoperator.operator.impl.CoreBoxHelmTh2Op;
import com.exactpro.th2.infraoperator.operator.impl.EstoreHelmTh2Op;
import com.exactpro.th2.infraoperator.operator.impl.JobHelmTh2Op;
import com.exactpro.th2.infraoperator.operator.impl.MstoreHelmTh2Op;
import com.exactpro.th2.infraoperator.operator.manager.impl.DefaultWatchManager;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.ContinuousTaskWorker;
import com.exactpro.th2.infraoperator.spec.strategy.redeploy.tasks.CheckResourceCacheTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Th2CrdController {

    private static final Logger logger = LoggerFactory.getLogger(Th2CrdController.class);

    public static void main(String[] args) {

        var watchManager = DefaultWatchManager.getInstance();
        PrometheusServer.start();
        OperatorMetrics.resetCacheErrors();
        try {
            RabbitMQContext.declareTopicExchange();

            watchManager.addTarget(MstoreHelmTh2Op::new);
            watchManager.addTarget(EstoreHelmTh2Op::new);
            watchManager.addTarget(BoxHelmTh2Op::new);
            watchManager.addTarget(CoreBoxHelmTh2Op::new);
            watchManager.addTarget(JobHelmTh2Op::new);

            watchManager.startInformers();

            ContinuousTaskWorker continuousTaskWorker = new ContinuousTaskWorker();
            continuousTaskWorker.add(new CheckResourceCacheTask(300));
            continuousTaskWorker.add(RabbitMQContext.buildGarbageCollectTask());
        } catch (Exception e) {
            logger.error("Exception in main thread", e);
            watchManager.stopInformers();
            watchManager.close();
            throw e;
        }
    }
}
