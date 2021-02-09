package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.operator.context.EventCounter;
import com.exactpro.th2.infraoperator.util.CustomResourceUtils;
import com.exactpro.th2.infraoperator.util.Strings;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericResourceEventHandler<T extends HasMetadata> implements ResourceEventHandler<T> {
    private static final Logger logger = LoggerFactory.getLogger(GenericResourceEventHandler.class);

    private ResourceEventHandler<T> eventHandler;

    public GenericResourceEventHandler(ResourceEventHandler<T> eventHandler) {
        this.eventHandler = eventHandler;
    }

    @Override
    public void onAdd(T obj) {

        try {
            long startDateTime = System.currentTimeMillis();

            if (Strings.nonePrefixMatch(obj.getMetadata().getNamespace(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
                return;
            }

            try {
                // temp fix: change thread name for logging purposes
                // TODO: propagate event id logging in code
                Thread.currentThread().setName(EventCounter.newEvent());
                String resourceLabel = CustomResourceUtils.annotationFor(obj);
                logger.debug("Received ADDED event for \"{}\"", resourceLabel);

                try {
                    eventHandler.onAdd(obj);
                } catch (Exception e) {
                    logger.error("Exception processing ADDED event for \"{}\"", resourceLabel, e);
                }

                long duration = System.currentTimeMillis() - startDateTime;
                logger.info("event for \"{}\" processed in {}ms", resourceLabel, duration);

            } finally {
                EventCounter.closeEvent();
                Thread.currentThread().setName("thread-" + Thread.currentThread().getId());
            }
        } catch (Exception e) {
            logger.error("Exception processing event", e);
        }
    }


    @Override
    public void onUpdate(T oldObj, T newObj) {

        try {
            long startDateTime = System.currentTimeMillis();

            if (Strings.nonePrefixMatch(oldObj.getMetadata().getNamespace(), OperatorConfig.INSTANCE.getNamespacePrefixes())
                    && Strings.nonePrefixMatch(newObj.getMetadata().getNamespace(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
                return;
            }

            try {
                // temp fix: change thread name for logging purposes
                // TODO: propagate event id logging in code
                Thread.currentThread().setName(EventCounter.newEvent());
                String resourceLabel = CustomResourceUtils.annotationFor(oldObj);
                logger.debug("Received MODIFIED event for \"{}\"", resourceLabel);

                try {
                    eventHandler.onUpdate(oldObj, newObj);
                } catch (Exception e) {
                    logger.error("Exception processing MODIFIED event for \"{}\"", resourceLabel, e);
                }

                long duration = System.currentTimeMillis() - startDateTime;
                logger.info("MODIFIED Event for \"{}\" processed in {}ms", resourceLabel, duration);

            } finally {
                EventCounter.closeEvent();
                Thread.currentThread().setName("thread-" + Thread.currentThread().getId());
            }
        } catch (Exception e) {
            logger.error("Exception processing event", e);
        }
    }


    @Override
    public void onDelete(T obj, boolean deletedFinalStateUnknown) {

        try {
            long startDateTime = System.currentTimeMillis();

            if (Strings.nonePrefixMatch(obj.getMetadata().getNamespace(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
                return;
            }

            try {
                // temp fix: change thread name for logging purposes
                // TODO: propagate event id logging in code
                Thread.currentThread().setName(EventCounter.newEvent());
                String resourceLabel = CustomResourceUtils.annotationFor(obj);
                logger.debug("Received DELETED event for \"{}\"", resourceLabel);

                try {
                    eventHandler.onDelete(obj, deletedFinalStateUnknown);
                } catch (Exception e) {
                    logger.error("Exception processing DELETED event for \"{}\"", resourceLabel, e);
                }

                long duration = System.currentTimeMillis() - startDateTime;
                logger.info("DELETED Event for {} processed in {}ms", resourceLabel, duration);

            } finally {
                EventCounter.closeEvent();
                Thread.currentThread().setName("thread-" + Thread.currentThread().getId());
            }
        } catch (Exception e) {
            logger.error("Exception processing event", e);
        }
    }
}
