package com.exactpro.th2.infraoperator.operator.manager.impl;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.configuration.OperatorConfig;
import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.mq.impl.RabbitMQContext;
import com.exactpro.th2.infraoperator.util.Strings;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.exactpro.th2.infraoperator.util.CustomResourceUtils.RESYNC_TIME;

public class NamespaceEventHandler implements ResourceEventHandler<Namespace>, Watcher<Namespace> {
    private static final Logger logger = LoggerFactory.getLogger(NamespaceEventHandler.class);

    public static NamespaceEventHandler newInstance(SharedInformerFactory sharedInformerFactory) {
        SharedIndexInformer<Namespace> namespaceInformer = sharedInformerFactory.sharedIndexInformerFor(
                Namespace.class,
                NamespaceList.class,
                RESYNC_TIME);

        var res = new NamespaceEventHandler();
        namespaceInformer.addEventHandler(res);
        return res;
    }

    @Override
    public void onAdd(Namespace namespace) {
        if (Strings.nonePrefixMatch(namespace.getMetadata().getName(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
            return;
        }

        logger.debug("Received ADDED event for namespace: \"{}\"", namespace.getMetadata().getName());
    }

    @Override
    public void onUpdate(Namespace oldNamespace, Namespace newNamespace) {
        if (Strings.nonePrefixMatch(oldNamespace.getMetadata().getName(), OperatorConfig.INSTANCE.getNamespacePrefixes())
                && Strings.nonePrefixMatch(newNamespace.getMetadata().getName(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
            return;
        }

        logger.debug("Received MODIFIED event for namespace: \"{}\"", newNamespace.getMetadata().getName());
    }

    @Override
    public void onDelete(Namespace namespace, boolean deletedFinalStateUnknown) {
        String namespaceName = namespace.getMetadata().getName();

        if (Strings.nonePrefixMatch(namespace.getMetadata().getName(), OperatorConfig.INSTANCE.getNamespacePrefixes())) {
            return;
        }

        logger.debug("Received DELETED event for namespace: \"{}\"", namespaceName);

        var lock = OperatorState.INSTANCE.getLock(namespaceName);

        try {
            lock.lock();

            logger.debug("Processing event DELETED for namespace: \"{}\"", namespaceName);
            RabbitMQContext.cleanupVHost(namespaceName);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void eventReceived(Action action, Namespace resource) {

    }

    @Override
    public void onClose(WatcherException cause) {
        throw new AssertionError("This method should not be called");
    }
}

