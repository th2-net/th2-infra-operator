/*
 * Copyright 2020-2024Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.configuration.ConfigLoader;
import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.operator.context.EventCounter;
import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.mq.RabbitMQContext;
import com.exactpro.th2.infraoperator.util.Strings;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.RESYNC_TIME;
import static com.exactpro.th2.infraoperator.util.WatcherUtils.createExceptionHandler;

public class NamespaceEventHandler implements ResourceEventHandler<Namespace>, Watcher<Namespace> {
    private static final Logger logger = LoggerFactory.getLogger(NamespaceEventHandler.class);

    private final EventQueue eventQueue;

    private final OperatorConfig config = ConfigLoader.getConfig();

    public static NamespaceEventHandler newInstance(SharedInformerFactory sharedInformerFactory,
                                                    EventQueue eventQueue) {
        SharedIndexInformer<Namespace> namespaceInformer = sharedInformerFactory.sharedIndexInformerFor(
                Namespace.class,
                RESYNC_TIME);

        var res = new NamespaceEventHandler(eventQueue);
        namespaceInformer.exceptionHandler(createExceptionHandler(Namespace.class));
        namespaceInformer.addEventHandler(res);
        return res;
    }

    public NamespaceEventHandler(EventQueue eventQueue) {
        this.eventQueue = eventQueue;
    }

    @Override
    public void onAdd(Namespace namespace) {
        if (Strings.nonePrefixMatch(namespace.getMetadata().getName(),
                config.getNamespacePrefixes())) {
            return;
        }

        logger.debug("Received ADDED event for namespace: \"{}\"", namespace.getMetadata().getName());
    }

    @Override
    public void onUpdate(Namespace oldNamespace, Namespace newNamespace) {
        if (Strings.nonePrefixMatch(oldNamespace.getMetadata().getName(),
                config.getNamespacePrefixes())
                && Strings.nonePrefixMatch(newNamespace.getMetadata().getName(),
                config.getNamespacePrefixes())) {
            return;
        }

        logger.debug("Received MODIFIED event for namespace: \"{}\"", newNamespace.getMetadata().getName());
    }

    @Override
    public void onDelete(Namespace namespace, boolean deletedFinalStateUnknown) {
        String namespaceName = namespace.getMetadata().getName();

        if (Strings.nonePrefixMatch(namespaceName, config.getNamespacePrefixes())) {
            return;
        }

        String resourceLabel = String.format("namespace:%s", namespaceName);
        String eventId = EventCounter.newEvent();
        logger.debug("Received DELETED event for namespace: \"{}\"", namespaceName);

        eventQueue.addEvent(EventQueue.generateEvent(
                eventId,
                resourceLabel,
                Action.DELETED,
                namespaceName,
                namespace,
                this));
    }

    @Override
    public void eventReceived(Action action, Namespace resource) {
        String namespaceName = resource.getMetadata().getName();

        var lock = OperatorState.INSTANCE.getLock(namespaceName);

        try {
            long startDateTime = System.currentTimeMillis();

            String resourceLabel = String.format("namespace:%s", namespaceName);

            try {
                lock.lock();

                logger.debug("Processing {} event for namespace: \"{}\"", action, namespaceName);
                RabbitMQContext.cleanupRabbit(namespaceName);
                logger.info("Deleted namespace {}", namespaceName);
            } catch (Exception e) {
                logger.error("Exception processing event for \"{}\"", resourceLabel, e);
            } finally {
                lock.unlock();
            }

            long duration = System.currentTimeMillis() - startDateTime;
            logger.info("Event for \"{}\" processed in {}ms", resourceLabel, duration);

        } catch (Exception e) {
            logger.error("Exception processing event", e);
        }
    }

    @Override
    public void onClose(WatcherException cause) {
        throw new AssertionError("This method should not be called");
    }
}

