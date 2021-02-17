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
    public static final String REFRESH_TOKEN_ALIAS = "refresh-token";


    private Watcher<T> watcher;
    private DefaultWatchManager.EventQueue<DefaultWatchManager.DispatcherEvent> eventQueue;

    public GenericResourceEventHandler(Watcher<T> watcher,
                                       DefaultWatchManager.EventQueue<DefaultWatchManager.DispatcherEvent> eventQueue) {
        this.watcher = watcher;
        this.eventQueue = eventQueue;
    }


    public String refreshToken(HasMetadata res) {

        var metadata = res.getMetadata();
        if (metadata == null)
            return null;

        var annotations= metadata.getAnnotations();
        if (annotations != null)
           return annotations.get(REFRESH_TOKEN_ALIAS);
        return null;
    }

    private String sourceHash(HasMetadata res) {

        String hash = ExtractUtils.sourceHash(res);
        if (hash != null)
            return "[" + hash.substring(0, 8) + "]";
        return "";
    }

    @Override
    public void onAdd(T obj) {

        if (Strings.nonePrefixMatch(obj.getMetadata().getNamespace(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
                return;
        }

        // temp fix: change thread name for logging purposes
        // TODO: propagate event id logging in code
        String resourceLabel = CustomResourceUtils.annotationFor(obj);
        String eventId = EventCounter.newEvent();
        logger.debug("Received ADDED event ({}) for \"{}\" {}, refresh-token={}",
                eventId,
                resourceLabel,
                sourceHash(obj),
                refreshToken(obj));

        eventQueue.addEvent(new DefaultWatchManager.DispatcherEvent(
                eventId,
                resourceLabel,
                Action.ADDED,
                obj,
                this));
    }


    @Override
    public void onUpdate(T oldObj, T newObj) {

        if (Strings.nonePrefixMatch(oldObj.getMetadata().getNamespace(), OperatorConfig.INSTANCE.getNamespacePrefixes())
                && Strings.nonePrefixMatch(newObj.getMetadata().getNamespace(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
            return;
        }

        // temp fix: change thread name for logging purposes
        // TODO: propagate event id logging in code
        String resourceLabel = CustomResourceUtils.annotationFor(oldObj);
        String eventId = EventCounter.newEvent();
        logger.debug("Received MODIFIED event ({}) for \"{}\" {}, refresh-token={}",
                eventId,
                resourceLabel,
                sourceHash(newObj),
                refreshToken(newObj));

        eventQueue.addEvent(new DefaultWatchManager.DispatcherEvent(
                eventId,
                resourceLabel,
                Action.MODIFIED,
                newObj,
                this));
    }


    @Override
    public void onDelete(T obj, boolean deletedFinalStateUnknown) {

        if (Strings.nonePrefixMatch(obj.getMetadata().getNamespace(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
            return;
        }

        // temp fix: change thread name for logging purposes
        // TODO: propagate event id logging in code
        String resourceLabel = CustomResourceUtils.annotationFor(obj);
        String eventId = EventCounter.newEvent();
        logger.debug("Received DELETED event ({}) for \"{}\" {}, refresh-token={}",
                eventId,
                resourceLabel,
                sourceHash(obj),
                refreshToken(obj));

        eventQueue.addEvent(new DefaultWatchManager.DispatcherEvent(
                eventId,
                resourceLabel,
                Action.DELETED,
                obj,
                this));
    }

    @Override
    public void eventReceived(Action action, T resource) {

        try {
            long startDateTime = System.currentTimeMillis();

            if (Strings.nonePrefixMatch(resource.getMetadata().getNamespace(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
                return;
            }

            try {
                // temp fix: change thread name for logging purposes
                // TODO: propagate event id logging in code
                String resourceLabel = CustomResourceUtils.annotationFor(resource);
                logger.debug("Received {} event for \"{}\" {}", action, resourceLabel, sourceHash(resource));

                try {
                    watcher.eventReceived(action, resource);
                } catch (Exception e) {
                    logger.error("Exception processing event for \"{}\"", resourceLabel, e);
                }

                long duration = System.currentTimeMillis() - startDateTime;
                logger.info("Event for \"{}\" processed in {}ms", resourceLabel, duration);

            } finally {
                EventCounter.closeEvent();
                Thread.currentThread().setName("thread-" + Thread.currentThread().getId());
            }
        } catch (Exception e) {
            logger.error("Exception processing event", e);
        }
    }

    @Override
    public void onClose(WatcherException cause) {
        throw new AssertionError("This method should not be called");
    }
}
