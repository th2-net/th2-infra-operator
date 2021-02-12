package com.exactpro.th2.infraoperator.operator.manager.impl;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;

public interface WatchHandler<T extends HasMetadata> extends ResourceEventHandler<T>, Watcher<T> {
}
