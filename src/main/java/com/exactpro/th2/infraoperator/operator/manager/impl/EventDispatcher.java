package com.exactpro.th2.infraoperator.operator.manager.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EventDispatcher extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(EventDispatcher.class);

    private final int N_THREADS = 10;
    private final int SUBMISSION_INTERVAL = 100;

    private final ExecutorService executor;
    private final ArrayList<String> workingNamespaces;
    private final DefaultWatchManager.EventStorage<DefaultWatchManager.DispatcherEvent> eventStorage;

    public EventDispatcher (DefaultWatchManager.EventStorage<DefaultWatchManager.DispatcherEvent> eventStorage) {
        this.eventStorage = eventStorage;
        this.executor = Executors.newFixedThreadPool(N_THREADS);
        this.workingNamespaces = new ArrayList<>();
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

            synchronized (workingNamespaces) {
                var el = this.eventStorage.popEvent(workingNamespaces);
                if (el == null) {
                    continue;
                }

                workingNamespaces.add(el.getCr().getMetadata().getNamespace());
                executor.submit(() -> {
                    try {
                        Thread.currentThread().setName(el.getEventId());
                        el.getCallback().eventReceived(el.getAction(), el.getCr());
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                    } finally {
                        synchronized (workingNamespaces) {
                            workingNamespaces.remove(el.getCr().getMetadata().getNamespace());
                        }
                    }
                });
            }
        }

        logger.info("EventDispatcher worker thread interrupted, stopping executor.");
        shutDown();
    }

    private void shutDown () {

    }
}
