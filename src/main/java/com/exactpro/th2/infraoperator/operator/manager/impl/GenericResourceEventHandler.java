package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.operator.context.EventCounter;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import com.exactpro.th2.infraoperator.util.Strings;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericResourceEventHandler<T extends HasMetadata> implements ResourceEventHandler<T>, Watcher<T> {
    private static final Logger logger = LoggerFactory.getLogger(GenericResourceEventHandler.class);

    private Watcher<T> watcher;
    private EventQueue eventQueue;

    public GenericResourceEventHandler(Watcher<T> watcher, EventQueue eventQueue) {
        this.watcher = watcher;
        this.eventQueue = eventQueue;
    }

    @Override
    public void onAdd(T obj) {

        if (Strings.nonePrefixMatch(obj.getMetadata().getNamespace(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
                return;
        }

        String resourceLabel = CustomResourceUtils.annotationFor(obj);
        String eventId = EventCounter.newEvent();
        logger.debug("Received ADDED event ({}) for \"{}\" {}, refresh-token={}",
                eventId,
                resourceLabel,
                ExtractUtils.sourceHash(obj, true),
                ExtractUtils.refreshToken(obj));

        eventQueue.addEvent(EventQueue.generateEvent(
                eventId,
                resourceLabel,
                Action.ADDED,
                obj.getMetadata().getNamespace(),
                obj,
                this));
    }


    @Override
    public void onUpdate(T oldObj, T newObj) {

        if (Strings.nonePrefixMatch(oldObj.getMetadata().getNamespace(), OperatorConfig.INSTANCE.getNamespacePrefixes())
                && Strings.nonePrefixMatch(newObj.getMetadata().getNamespace(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
            return;
        }

        String resourceLabel = CustomResourceUtils.annotationFor(oldObj);
        String eventId = EventCounter.newEvent();
        logger.debug("Received MODIFIED event ({}) for \"{}\" {}, refresh-token={}",
                eventId,
                resourceLabel,
                ExtractUtils.sourceHash(newObj, true),
                ExtractUtils.refreshToken(newObj));

        eventQueue.addEvent(EventQueue.generateEvent(
                eventId,
                resourceLabel,
                Action.MODIFIED,
                newObj.getMetadata().getNamespace(),
                newObj,
                this));
    }


    @Override
    public void onDelete(T obj, boolean deletedFinalStateUnknown) {

        if (Strings.nonePrefixMatch(obj.getMetadata().getNamespace(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
            return;
        }

        String resourceLabel = CustomResourceUtils.annotationFor(obj);
        String eventId = EventCounter.newEvent();
        logger.debug("Received DELETED event ({}) for \"{}\" {}, refresh-token={}",
                eventId,
                resourceLabel,
                ExtractUtils.sourceHash(obj, true),
                ExtractUtils.refreshToken(obj));

        eventQueue.addEvent(EventQueue.generateEvent(
                eventId,
                resourceLabel,
                Action.DELETED,
                obj.getMetadata().getNamespace(),
                obj,
                this));
    }

    @Override
    public void eventReceived(Action action, T resource) {

        try {
            long startDateTime = System.currentTimeMillis();

            String resourceLabel = CustomResourceUtils.annotationFor(resource);
            logger.debug("Processing {} event for \"{}\" {}", action, resourceLabel, ExtractUtils.sourceHash(resource, true));

            try {
                // let handler process the message
                watcher.eventReceived(action, resource);
            } catch (Exception e) {
                logger.error("Exception processing event for \"{}\"", resourceLabel, e);
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
