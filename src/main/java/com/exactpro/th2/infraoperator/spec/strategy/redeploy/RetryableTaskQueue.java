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

package com.exactpro.th2.infraoperator.spec.strategy.redeploy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RetryableTaskQueue {
    private static final Logger logger = LoggerFactory.getLogger(RetryableTaskQueue.class);

    private static final int THREAD_POOL_SIZE = 3;

    public interface Task extends Runnable {
        String getName();

        long getRetryDelay();
    }

    private class RetryableTask implements Runnable {
        Task task;

        private RetryableTask(Task task) {
            this.task = task;
        }

        @Override
        public void run() {
            try {
                logger.info("Executing task: \"{}\"", task.getName());
                task.run();
                logger.info("Task: \"{}\" completed successfully", task.getName());
                completeTask(task);
            } catch (Exception e) {
                RetryableTaskQueue.this.addTask(this, true);
            }
        }
    }

    private final Map<String, RetryableTask> taskMap;

    private final ScheduledExecutorService taskScheduler;

    public RetryableTaskQueue() {
        taskMap = new HashMap<>();
        taskScheduler = new ScheduledThreadPoolExecutor(THREAD_POOL_SIZE);
    }

    private synchronized void addTask(RetryableTask retryableTask, boolean delayed) {
        taskMap.put(retryableTask.task.getName(), retryableTask);
        taskScheduler.schedule(retryableTask, delayed ? retryableTask.task.getRetryDelay() : 0, TimeUnit.SECONDS);
    }

    public synchronized void add(Task task) {
        if (!taskMap.containsKey(task.getName())) {
            addTask(new RetryableTask(task), false);
        }
    }

    public synchronized void add(Task task, boolean delayed) {
        if (!taskMap.containsKey(task.getName())) {
            addTask(new RetryableTask(task), delayed);
        }
    }

    private synchronized void completeTask(Task task) throws IllegalStateException {
        String taskName = task.getName();
        if (!taskMap.containsKey(taskName)) {
            throw new IllegalStateException("Task \"" + taskName + "\" was not found in active task list");
        }
        taskMap.remove(taskName);
    }

    public void shutdown() {
        taskScheduler.shutdown();
    }
}
