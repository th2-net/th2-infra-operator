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

import com.exactpro.th2.infraoperator.spec.strategy.redeploy.tasks.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ContinuousTaskWorker {
    private static final Logger logger = LoggerFactory.getLogger(ContinuousTaskWorker.class);

    private static final int THREAD_POOL_SIZE = 2;

    private final Map<String, Task> taskMap = new HashMap<>();

    private final ScheduledExecutorService taskScheduler = new ScheduledThreadPoolExecutor(THREAD_POOL_SIZE);

    public synchronized void add(Task task) {
        if (!taskMap.containsKey(task.getName())) {
            taskMap.put(task.getName(), task);
            taskScheduler.scheduleWithFixedDelay(task, task.getRetryDelay(), task.getRetryDelay(), TimeUnit.SECONDS);
            logger.info("Added task '{}' to scheduler", task.getName());
        } else {
            logger.info("Task '{}' is already present in scheduler. Will not be added again", task.getName());
        }
    }

    public void shutdown() {
        taskScheduler.shutdown();
    }
}
