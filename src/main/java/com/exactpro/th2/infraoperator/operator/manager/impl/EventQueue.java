package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.spec.dictionary.Th2Dictionary;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.Watcher;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

public class EventQueue {

    private static final Logger logger = LoggerFactory.getLogger(EventQueue.class);

    private final List<HPEvent> hPEvents;
    private final List<Event> events;
    private final LinkedList<String> workingNamespaces;

    public EventQueue() {
        this.hPEvents = new LinkedList<>();
        this.events = new LinkedList<>();
        this.workingNamespaces = new LinkedList<>();
    }

    @Getter
    @AllArgsConstructor
    public static class Event {
        private String eventId;
        private String annotation;
        private Watcher.Action action;
        private String namespace;
        private HasMetadata resource;
        private Watcher callback;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EventQueue.Event)) return false;

            return (getAnnotation().equals(((Event) o).getAnnotation()) && getAction().equals(((Event) o).getAction()));
        }

        /*
            Replace should only happen when we have
            two objects with same annotation and action
         */
        public void replace (Event event) {
            this.eventId = event.eventId;
            this.resource = event.resource;
        }
    }

    /*
        High priority events which will be added
        in high priority queue
     */
    @Getter
    public static class HPEvent extends Event {

        public HPEvent(String eventId, String annotation, Watcher.Action action, String namespace, HasMetadata resource, Watcher callback) {
            super(eventId, annotation, action, namespace, resource, callback);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EventQueue.HPEvent)) return false;

            return (getAnnotation().equals(((Event) o).getAnnotation()) && getAction().equals(((Event) o).getAction()));
        }
    }

    public static Event generateEvent(
            String eventId,
            String annotation,
            Watcher.Action action,
            String namespace,
            HasMetadata resource,
            Watcher callback) {
        if (resource instanceof ConfigMap
                || resource instanceof Th2Link
                || resource instanceof Th2Dictionary
                || resource instanceof Namespace) {
            return new HPEvent (eventId, annotation, action, namespace, resource, callback);
        }

        return new Event(eventId, annotation, action, namespace, resource, callback);
    }

    private int getIndexForEvent(Event event, List<? extends Event> eventQueue) {
        // Namespace events should not be substituted
        if (event.getResource() instanceof Namespace) {
            return eventQueue.size();
        }

        // try to substitute old event with new one
        for (int i = eventQueue.size() - 1; i >= 0; i--) {
            Event el = eventQueue.get(i);
            if (el.getAnnotation().equals(event.getAnnotation()) && !el.getAction().equals(event.getAction()))
                break;
            if (el.getResource() instanceof Namespace && el.getNamespace().equals(event.getNamespace())) {
                logger.info("Namespace event detected, can't enforce substitution logic further");
                break;
            }

            if (el.equals(event)) {
                logger.debug("Substituting {} with {}, {} event(s) present in the queue",
                        el.getEventId(),
                        event.getEventId(),
                        eventQueue.size());

                try {
                    var oldRV = el.getResource().getMetadata().getResourceVersion();
                    var newRV = el.getResource().getMetadata().getResourceVersion();
                    if (oldRV != null && newRV != null && Long.valueOf(newRV) < Long.valueOf(oldRV))
                        logger.warn("Substituted with older resource (old.resourceVersion={}, new.resourceVersion={})",
                                oldRV,
                                newRV);
                } catch (Exception e) {
                    logger.error("Exception checking resourceVersion", e);
                }

                return i;
            }
        }

        // no event could be substituted, add it to the end
        return eventQueue.size();
    }

    public synchronized void addEvent(Event event) {
        if (event.getResource() instanceof Namespace) {
            hPEvents.add((HPEvent) event);
            events.add(event);

            // Log state of queues
            logger.debug("Enqueued {}, {} event(s) present in the HP queue, {} event(s) in the queue",
                    event.getEventId(),
                    hPEvents.size(),
                    events.size());

            return;
        }

        if (event instanceof HPEvent) {
            int index = getIndexForEvent(event, hPEvents);

            if (index == hPEvents.size()) {
                hPEvents.add((HPEvent) event);
            } else {
                hPEvents.get(index).replace(event);
            }
        } else {
            //event is instanceof Event
            int index = getIndexForEvent(event, events);

            if (index == events.size()) {
                events.add(event);
            } else {
                events.get(index).replace(event);
            }
        }

        // Log state of queues
        logger.debug("Enqueued {}, {} event(s) present in the HP queue, {} event(s) in the queue",
                event.getEventId(),
                hPEvents.size(),
                events.size());
    }

    private Event withdrawEventFromQueue (List<? extends Event> eventQueue) {
        for (int i = 0; i < eventQueue.size(); i++) {
            String namespace = eventQueue.get(i).getNamespace();

            if (!workingNamespaces.contains(namespace)) {
                Event event = eventQueue.remove(i);
                addNamespace(namespace);
                return event;
            }
        }

        return null;
    }

    private void removeFirstNamespaceEvent () {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getResource() instanceof Namespace) {
                events.remove(i);
                break;
            }
        }
    }

    public synchronized Event withdrawEvent() {
        Event event;

        event = withdrawEventFromQueue(hPEvents);

        /*
            Since namespace events are being added in both queue,
            we need to remove namespace event from other queue as well
         */
        if (event != null && event.getResource() instanceof Namespace) {
            removeFirstNamespaceEvent();
        }

        /*
            If hp queue doesn't contain valid elements,
            we should check other queue
         */
        if (event == null) {
            event = withdrawEventFromQueue(events);
        }

        if (event != null) {
            logger.debug("Withdrawn {}, {} event(s) present in the HP queue, {} event(s) in queue",
                    event.getEventId(),
                    hPEvents.size(),
                    events.size());
        }

        return event;
    }

    private void addNamespace(String namespace) {
        workingNamespaces.add(namespace);
    }

    public synchronized void closeEvent(Event event) {
        workingNamespaces.remove(event.getNamespace());
    }
}
