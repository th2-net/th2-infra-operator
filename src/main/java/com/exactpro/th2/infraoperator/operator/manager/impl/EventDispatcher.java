package com.exactpro.th2.infraoperator.operator.manager.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EventDispatcher extends Thread implements EventQueue.Awareable {

    private static final Logger logger = LoggerFactory.getLogger(EventDispatcher.class);

    private static final int N_THREADS = 10;
    private static final int EVENT_WAIT_TIME_MS = 3000;

    private final ExecutorService executor;
    private final EventQueue eventQueue;
    private final Object monitor;

    public EventDispatcher () {
        this.monitor = new Object();
        this.executor = Executors.newFixedThreadPool(N_THREADS);
        this.eventQueue = new EventQueue(this);
    }


    public EventQueue getEventQueue() {
        return eventQueue;
    }

    @Override
    public void run() {
        logger.info("EventDispatcher has been started");

        while (!isInterrupted()) {

            var el = eventQueue.withdrawEvent();
            if (el == null) {
                awaitEvent();
                continue;
            }

            executor.submit(() -> {
                String threadName = Thread.currentThread().getName();
                try {
                    Thread.currentThread().setName(el.getEventId());
                    el.getCallback().eventReceived(el.getAction(), el.getResource());
                } catch (Exception e) {
                    logger.error("Exception dispatching event {}", el.getEventId(), e);
                } finally {
                    eventQueue.closeEvent(el);
                    Thread.currentThread().setName(threadName);
                    beAware();
                }
            });
        }

        logger.info("EventDispatcher worker thread interrupted, stopping executor.");
        shutDown();
    }


    @Override
    public void beAware() {
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }


    void awaitEvent() {
        synchronized (monitor) {
            try {
                monitor.wait(EVENT_WAIT_TIME_MS);
            } catch (InterruptedException ignored) {
            }
        }
    }


    private void shutDown () {

    }

}
