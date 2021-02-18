package com.exactpro.th2.infraoperator.operator.manager.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EventDispatcher extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(EventDispatcher.class);

    private final int N_THREADS = 10;
    private final int EVENT_WAIT_TIME_MS = 100;

    private final ExecutorService executor;
    private final DefaultWatchManager.EventQueue<DefaultWatchManager.DispatcherEvent> eventQueue;

    public EventDispatcher (DefaultWatchManager.EventQueue<DefaultWatchManager.DispatcherEvent> eventQueue) {
        this.eventQueue = eventQueue;
        this.executor = Executors.newFixedThreadPool(N_THREADS);
    }


    @Override
    public void run() {
        logger.info("EventDispatcher has been started");

        while (!isInterrupted()) {

            var el = eventQueue.withdrawEvent();
            if (el == null) {
                try {
                    Thread.sleep(EVENT_WAIT_TIME_MS);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }

            executor.submit(() -> {
                String threadName = Thread.currentThread().getName();
                try {
                    Thread.currentThread().setName(el.getEventId());
                    el.getCallback().eventReceived(el.getAction(), el.getResource());
                } catch (Exception e) {
                    logger.error("Exception dispatching event {}", el.getEventId(), e.getMessage());
                } finally {
                    eventQueue.closeEvent(el);
                    Thread.currentThread().setName(threadName);
                }
            });
        }

        logger.info("EventDispatcher worker thread interrupted, stopping executor.");
        shutDown();
    }

    private void shutDown () {

    }
}
