package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.operator.context.EventCounter;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.Strings;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericResourceEventHandler<T extends HasMetadata> implements ResourceEventHandler<T>, Watcher<T> {
    private static final Logger logger = LoggerFactory.getLogger(GenericResourceEventHandler.class);
    public static final String KEY_SOURCE_HASH = "th2.exactpro.com/source-hash";


    private Watcher<T> watcher;
    private DefaultWatchManager.EventContainer<DefaultWatchManager.DispatcherEvent> eventContainer;

    public GenericResourceEventHandler(Watcher<T> watcher,
                                       DefaultWatchManager.EventContainer<DefaultWatchManager.DispatcherEvent> eventContainer) {
        this.watcher = watcher;
        this.eventContainer = eventContainer;
    }

    private String sourceHash(HasMetadata res) {

        if (res.getMetadata() != null && res.getMetadata().getAnnotations()!=null) {
            String hash = res.getMetadata().getAnnotations().get(KEY_SOURCE_HASH);
            if (hash != null)
                return "[" + hash.substring(0, 8) + "]";
        }
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
        logger.debug("Received ADDED event ({}) for \"{}\" {}", eventId, resourceLabel, sourceHash(obj));

        eventContainer.addEvent(new DefaultWatchManager.DispatcherEvent(
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
        logger.debug("Received MODIFIED event ({}) for \"{}\" {}", eventId, resourceLabel, sourceHash(newObj));

        eventContainer.addEvent(new DefaultWatchManager.DispatcherEvent(
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
        logger.debug("Received DELETED event ({}) for \"{}\" {}", eventId, resourceLabel, sourceHash(obj));

        eventContainer.addEvent(new DefaultWatchManager.DispatcherEvent(
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

    }
}
