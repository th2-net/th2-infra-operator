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

import com.exactpro.th2.infraoperator.configuration.ConfigLoader;
import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
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
import com.exactpro.th2.infraoperator.util.RabbitMQUtils;
import com.exactpro.th2.infraoperator.util.Utils;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.logging.log4j.core.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.exactpro.th2.infraoperator.util.KubernetesUtils.createKubernetesClient;

public class Th2CrdController implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Th2CrdController.class);

    private final PrometheusServer prometheusServer;

    private final KubernetesClient kubClient;

    private final DefaultWatchManager watchManager;

    private final RabbitMQContext rabbitMQContext;

    private final ContinuousTaskWorker continuousTaskWorker;

    public Th2CrdController() throws IOException, URISyntaxException {
        OperatorConfig config = ConfigLoader.loadConfiguration();
        prometheusServer = new PrometheusServer(config.getPrometheusConfiguration());
        kubClient = createKubernetesClient();
        rabbitMQContext = new RabbitMQContext(config.getRabbitMQManagement());
        watchManager = new DefaultWatchManager(kubClient, rabbitMQContext);
        continuousTaskWorker = new ContinuousTaskWorker();

        OperatorMetrics.resetCacheErrors();
        RabbitMQUtils.deleteRabbitMQRubbish(kubClient, rabbitMQContext);
        // TODO: topic exchange should be removed when all namespaces are removed / disabled
        rabbitMQContext.declareTopicExchange();

        watchManager.addTarget(MstoreHelmTh2Op::new);
        watchManager.addTarget(EstoreHelmTh2Op::new);
        watchManager.addTarget(BoxHelmTh2Op::new);
        watchManager.addTarget(CoreBoxHelmTh2Op::new);
        watchManager.addTarget(JobHelmTh2Op::new);

        watchManager.startInformers();
        continuousTaskWorker.add(new CheckResourceCacheTask(300));
    }

    public static void main(String[] args) {
        Deque<AutoCloseable> resources = new ConcurrentLinkedDeque<>();
        Lock lock = new ReentrantLock();
        Condition condition = lock.newCondition();

        configureShutdownHook(resources, lock, condition);

        try {
            if (args.length > 0) {
                configureLogger(args[0]);
            }
            Th2CrdController controller = new Th2CrdController();
            resources.add(controller);

            awaitShutdown(lock, condition);
        } catch (Exception e) {
            LOGGER.error("Exception in main thread", e);
            System.exit(1);
        }
    }

    @Override
    public void close() {
        Utils.close(prometheusServer, "Prometheus server");
        Utils.close(kubClient, "Kubernetes client");
        Utils.close(watchManager, "Watch manager");
        Utils.close(rabbitMQContext, "RabbitMQ context");
        Utils.close(continuousTaskWorker, "Continuous task worker");
    }

    private static void configureLogger(String filePath) {
        Path path = Path.of(filePath);
        if (Files.exists(path)) {
            LoggerContext loggerContext = LoggerContext.getContext(false);
            loggerContext.setConfigLocation(path.toUri());
            loggerContext.reconfigure();
            LOGGER.info("Logger configuration from {} file is applied", path);
        }
    }

    private static void configureShutdownHook(Deque<AutoCloseable> resources, Lock lock, Condition condition) {
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> {
                    LOGGER.info("Shutdown start");
                    lock.lock();
                    try {
                        condition.signalAll();
                    } finally {
                        lock.unlock();
                    }
                    resources.descendingIterator().forEachRemaining((resource) -> {
                        try {
                            resource.close();
                        } catch (Exception e) {
                            LOGGER.error("Cannot close resource {}", resource.getClass(), e);
                        }
                    });
                    LOGGER.info("Shutdown end");
                },
                "Shutdown hook"
        ));
    }

    private static void awaitShutdown(Lock lock, Condition condition) throws InterruptedException {
        lock.lock();
        try {
            LOGGER.info("Wait shutdown");
            condition.await();
            LOGGER.info("App shutdown");
        } finally {
            lock.unlock();
        }
    }
}
