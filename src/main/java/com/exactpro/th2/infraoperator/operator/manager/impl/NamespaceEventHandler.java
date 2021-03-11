package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.operator.context.EventCounter;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.mq.impl.RabbitMQContext;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
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

public class NamespaceEventHandler implements ResourceEventHandler<Namespace>, Watcher<Namespace> {
    private static final Logger logger = LoggerFactory.getLogger(NamespaceEventHandler.class);

    private EventQueue eventQueue;

    public static NamespaceEventHandler newInstance(SharedInformerFactory sharedInformerFactory,
                                                    EventQueue eventQueue) {
        SharedIndexInformer<Namespace> namespaceInformer = sharedInformerFactory.sharedIndexInformerFor(
                Namespace.class,
                RESYNC_TIME);

        var res = new NamespaceEventHandler(eventQueue);
        namespaceInformer.addEventHandler(res);
        return res;
    }

    public NamespaceEventHandler (EventQueue eventQueue) {
        this.eventQueue = eventQueue;
    }

    @Override
    public void onAdd(Namespace namespace) {
        if (Strings.nonePrefixMatch(namespace.getMetadata().getName(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
            return;
        }

        logger.debug("Received ADDED event for namespace: \"{}\"", namespace.getMetadata().getName());
    }

    @Override
    public void onUpdate(Namespace oldNamespace, Namespace newNamespace) {
        if (Strings.nonePrefixMatch(oldNamespace.getMetadata().getName(), OperatorConfig.INSTANCE.getNamespacePrefixes())
                && Strings.nonePrefixMatch(newNamespace.getMetadata().getName(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
            return;
        }

        logger.debug("Received MODIFIED event for namespace: \"{}\"", newNamespace.getMetadata().getName());
    }

    @Override
    public void onDelete(Namespace namespace, boolean deletedFinalStateUnknown) {
        String namespaceName = namespace.getMetadata().getName();

        if (Strings.nonePrefixMatch(namespaceName, OperatorConfig.INSTANCE.getNamespacePrefixes())) {
            return;
        }

        String resourceLabel = CustomResourceUtils.annotationFor(namespace);
        String eventId = EventCounter.newEvent();
        logger.debug("Received DELETED event ({}) for \"{}\" {}",
                eventId,
                resourceLabel,
                ExtractUtils.sourceHash(namespace, true));

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

            String resourceLabel = CustomResourceUtils.annotationFor(resource);

            try {
                lock.lock();

                logger.debug("Processing {} event for namespace: \"{}\"", action, namespaceName);
                RabbitMQContext.cleanupVHost(namespaceName);
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

