/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.infraoperator.metrics.OperatorMetrics;
import com.exactpro.th2.infraoperator.spec.dictionary.Th2Dictionary;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

public class EventQueue {

    private static final Logger logger = LoggerFactory.getLogger(EventQueue.class);

    private final List<PriorityEvent> priorityEvents;

    private final List<Event> regularEvents;

    private final LinkedList<String> workingNamespaces;

    private final Awareable monitor;

    public EventQueue(Awareable monitor) {
        this.priorityEvents = new LinkedList<>();
        this.regularEvents = new LinkedList<>();
        this.workingNamespaces = new LinkedList<>();
        this.monitor = monitor;
    }

    public static class Event {

        private String eventId;

        private String annotation;

        private final Watcher.Action action;

        private final String namespace;

        private HasMetadata resource;

        private final Watcher callback;

        public Event(String eventId, String annotation, Watcher.Action action, String namespace, HasMetadata resource
                , Watcher callback) {
            this.eventId = eventId;
            this.annotation = annotation;
            this.action = action;
            this.namespace = namespace;
            this.resource = resource;
            this.callback = callback;
        }

        public boolean canReplaceWithEvent(Event event) {
            return this.getAnnotation().equals(event.getAnnotation())
                    && !this.getAction().equals(Watcher.Action.DELETED)
                    && !event.getAction().equals(Watcher.Action.DELETED);
        }

        /*
            Replace should only happen when we have
            two objects with same annotation and action
         */
        public void replace(Event event) {
            this.eventId = event.eventId;
            this.annotation = event.annotation;
            this.resource = event.resource;
        }

        @Override
        public String toString() {
            return "Event{" +
                    "eventId='" + getEventId() + '\'' +
                    ", annotation='" + getAnnotation() + '\'' +
                    ", action=" + getAction() +
                    '}';
        }

        public String getEventId() {
            return this.eventId;
        }

        public String getAnnotation() {
            return this.annotation;
        }

        public Watcher.Action getAction() {
            return this.action;
        }

        public String getNamespace() {
            return this.namespace;
        }

        public HasMetadata getResource() {
            return this.resource;
        }

        public Watcher getCallback() {
            return this.callback;
        }
    }

    /*
        High priority events which will be added
        in high priority queue
     */
    public static class PriorityEvent extends Event {

        public PriorityEvent(String eventId, String annotation, Watcher.Action action, String namespace,
                             HasMetadata resource, Watcher callback) {
            super(eventId, annotation, action, namespace, resource, callback);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PriorityEvent)) {
                return false;
            }
            return getAnnotation().equals(((PriorityEvent) o).getAnnotation()) &&
                    getAction().equals(((PriorityEvent) o).getAction());
        }

        @Override
        public boolean canReplaceWithEvent(Event event) {
            return this.equals(event);
        }

        @Override
        public String toString() {
            return "PriorityEvent{" +
                    "eventId='" + getEventId() + '\'' +
                    ", annotation='" + getAnnotation() + '\'' +
                    ", action=" + getAction() +
                    '}';
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
                || resource instanceof Th2Dictionary
                || resource instanceof Namespace) {
            return new PriorityEvent(eventId, annotation, action, namespace, resource, callback);
        }

        return new Event(eventId, annotation, action, namespace, resource, callback);
    }

    private int getIndexForEvent(Event event, List<? extends Event> eventQueue) {
        // try to substitute old event with new one
        for (int i = eventQueue.size() - 1; i >= 0; i--) {
            Event e = eventQueue.get(i);

            if (e.canReplaceWithEvent(event)) {
                logger.debug("Substituting {} with {}",
                        e.getEventId(),
                        event.getEventId());

                try {
                    var oldRV = e.getResource().getMetadata().getResourceVersion();
                    var newRV = e.getResource().getMetadata().getResourceVersion();
                    if (oldRV != null && newRV != null && Long.valueOf(newRV) < Long.valueOf(oldRV)) {
                        logger.warn("Substituted with older resource (old.resourceVersion={}, new.resourceVersion={})",
                                oldRV, newRV);
                    }
                } catch (Exception ex) {
                    logger.error("Exception checking resourceVersion", ex);
                }

                return i;
            }
        }

        // no event could be substituted, add it to the end
        return eventQueue.size();
    }

    public synchronized void addEvent(Event event) {

        try {
            if (event.getResource() instanceof Namespace) {
                if (event.action.equals(Watcher.Action.DELETED)) {
                    preemptAllEventsForNamespace(event.getNamespace());
                }

                priorityEvents.add((PriorityEvent) event);
                OperatorMetrics.setPriorityEventCount(priorityEvents.size(), event.namespace);
                // Log state of queues
                logger.debug("Preempted namespace {}, {} event(s) present in the priority queue, " +
                                "{} event(s) in the regular queue",
                        event.getNamespace(), priorityEvents.size(), regularEvents.size());

                return;
            }

            if (event instanceof PriorityEvent) {
                int index = getIndexForEvent(event, priorityEvents);

                if (index == priorityEvents.size()) {
                    priorityEvents.add((PriorityEvent) event);
                    OperatorMetrics.setPriorityEventCount(priorityEvents.size(), event.namespace);
                } else {
                    priorityEvents.get(index).replace(event);
                }
            } else {
                //event is instanceof Event
                int index = getIndexForEvent(event, regularEvents);

                if (index == regularEvents.size()) {
                    regularEvents.add(event);
                    OperatorMetrics.setRegularEventCount(regularEvents.size(), event.namespace);
                } else {
                    regularEvents.get(index).replace(event);
                }
            }

            // Log state of queues
            logger.debug("Enqueued {}, {} event(s) present in the priority queue, {} event(s) in the regular queue",
                    event.getEventId(), priorityEvents.size(), regularEvents.size());
        } catch (Exception e) {
            logger.error("Exception enqueueing {}, {} event(s) present in the priority queue, " +
                            "{} event(s) in the regular queue",
                    event.getEventId(), priorityEvents.size(), regularEvents.size(), e);
        } finally {
            if (monitor != null) {
                monitor.beAware();
            }
        }
    }

    private Event withdrawEventFromQueue(List<? extends Event> eventQueue) {
        for (int i = 0; i < eventQueue.size(); i++) {
            String namespace = eventQueue.get(i).getNamespace();

            if (!workingNamespaces.contains(namespace)) {
                Event event = eventQueue.remove(i);
                lockNamespace(namespace);
                return event;
            }
        }

        return null;
    }

    public void preemptEventsForQueue(List<? extends Event> queue, String namespace) {
        logger.debug("Preempting events for namespace {}", namespace);

        int cnt = 0;
        var iterator = queue.iterator();
        while (iterator.hasNext()) {
            var event = iterator.next();

            if (event.getNamespace().equals(namespace)) {
                cnt++;
                iterator.remove();
            }

        }

        logger.debug("Preempted {} events from queue", cnt);
    }

    public void preemptAllEventsForNamespace(String namespace) {

        logger.debug("Preempting priorityEvents");
        preemptEventsForQueue(priorityEvents, namespace);
        logger.debug("Preempting regularEvents");
        preemptEventsForQueue(regularEvents, namespace);
    }

    public synchronized Event withdrawEvent() {

        Event event = withdrawEventFromQueue(priorityEvents);

        /*
            If priorityQueue queue doesn't contain valid elements,
            we should check regularQueue
         */
        if (event == null) {
            event = withdrawEventFromQueue(regularEvents);
        }

        if (event != null) {
            OperatorMetrics.setPriorityEventCount(priorityEvents.size(), event.namespace);
            OperatorMetrics.setRegularEventCount(regularEvents.size(), event.namespace);
            logger.debug("Withdrawn {}, {} event(s) present in the priority queue, {} event(s) in the regular queue",
                    event.getEventId(),
                    priorityEvents.size(),
                    regularEvents.size());
        }

        return event;
    }

    private void lockNamespace(String namespace) {
        workingNamespaces.add(namespace);
    }

    public synchronized void closeEvent(Event event) {
        workingNamespaces.remove(event.getNamespace());
    }

    public interface Awareable {
        void beAware();
    }
}
