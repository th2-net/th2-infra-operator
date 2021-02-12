package com.exactpro.th2.infraoperator.operator.manager.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EventDispatcher extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(EventDispatcher.class);

    private final int N_THREADS = 10;
    private final int SUBMISSION_INTERVAL = 100;

    private final ExecutorService executor;
    private final DefaultWatchManager.EventContainer<DefaultWatchManager.DispatcherEvent> eventContainer;

    public EventDispatcher (DefaultWatchManager.EventContainer<DefaultWatchManager.DispatcherEvent> eventContainer) {
        this.eventContainer = eventContainer;
        this.executor = Executors.newFixedThreadPool(N_THREADS);
    }


    @Override
    public void run() {
        logger.info("EventDispatcher has been started");

        while (!isInterrupted()) {
            try {
                Thread.sleep(SUBMISSION_INTERVAL);
            } catch (InterruptedException e) {
                break;
            }

            var el = this.eventContainer.popEvent();
            if (el == null) {
                continue;
            }

            executor.submit(() -> {
                try {
                    Thread.currentThread().setName(el.getEventId());
                    el.getCallback().eventReceived(el.getAction(), el.getCr());
                } catch (Exception e) {
                    logger.error(e.getMessage());
                } finally {
                    eventContainer.removeNamespace(el.getCr().getMetadata().getNamespace());
                }
            });
        }

        logger.info("EventDispatcher worker thread interrupted, stopping executor.");
        shutDown();
    }

    private void shutDown () {

    }
}
