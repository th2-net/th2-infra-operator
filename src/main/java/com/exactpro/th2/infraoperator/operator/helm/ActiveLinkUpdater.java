package com.exactpro.th2.infraoperator.operator.helm;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.model.box.schema.link.EnqueuedLink;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.relation.dictionaries.DictionaryBinding;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCoupling;
import com.exactpro.th2.infraoperator.spec.link.relation.pins.PinCouplingGRPC;
import com.exactpro.th2.infraoperator.util.ExtractUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public abstract class ActiveLinkUpdater {
    private static final Logger logger = LoggerFactory.getLogger(ActiveLinkUpdater.class);

    public List<Th2CustomResource> updateLinks(Th2CustomResource resource) {

        var resourceName = ExtractUtils.extractName(resource);

        var resNamespace = ExtractUtils.extractNamespace(resource);

        var lSingleton = OperatorState.INSTANCE;

        var linkResources = lSingleton.getLinkResources(resNamespace);

        var allActiveLinksOld = new ArrayList<>(lSingleton.getAllBoxesActiveLinks(resNamespace));

        var mqActiveLinks = new ArrayList<>(lSingleton.getMqActiveLinks(resNamespace));

        var grpcActiveLinks = new ArrayList<>(lSingleton.getGrpcActiveLinks(resNamespace));

        var dictionaryActiveLinks = new ArrayList<>(lSingleton.getDictionaryActiveLinks(resNamespace));

        var oldMsgStSize = lSingleton.getMsgStorageActiveLinks(resNamespace).size();

        var oldEventStSize = lSingleton.getEventStorageActiveLinks(resNamespace).size();

        var oldMqSize = lSingleton.getGeneralMqActiveLinks(resNamespace).size();

        var oldGrpcSize = grpcActiveLinks.size();

        var oldDicSize = dictionaryActiveLinks.size();

        refreshQueues(resource);

        refreshMqLinks(linkResources, mqActiveLinks, resource);

        refreshGrpcLinks(linkResources, grpcActiveLinks, resource);

        refreshDictionaryLinks(linkResources, dictionaryActiveLinks, resource);

        lSingleton.setMqActiveLinks(resNamespace, mqActiveLinks);

        lSingleton.setGrpcActiveLinks(resNamespace, grpcActiveLinks);

        lSingleton.setDictionaryActiveLinks(resNamespace, dictionaryActiveLinks);

        var newMsgStSize = lSingleton.getMsgStorageActiveLinks(resNamespace).size();

        var newEventStSize = lSingleton.getEventStorageActiveLinks(resNamespace).size();

        var newMqSize = lSingleton.getGeneralMqActiveLinks(resNamespace).size();

        var newGrpcSize = grpcActiveLinks.size();

        var newDicSize = dictionaryActiveLinks.size();

        var allActiveLinksNew = new ArrayList<>(lSingleton.getAllBoxesActiveLinks(resNamespace));

        var hiddenLinks = newMsgStSize + newEventStSize;

        var allSize = allActiveLinksNew.size() + newDicSize - hiddenLinks;

        logger.info(String.format(
                "Updated active links in namespace '%s'. Active links: %s (hidden %s). " +
                        "Details: %+d mq, %+d grpc, %+d dictionary, %+d hidden[mstore], %+d hidden[estore]",
                resNamespace, allSize, hiddenLinks,
                newMqSize - oldMqSize,
                newGrpcSize - oldGrpcSize,
                newDicSize - oldDicSize,
                newMsgStSize - oldMsgStSize,
                newEventStSize - oldEventStSize
        ));

        var linkedResources = getLinkedResources(resource, allActiveLinksOld, allActiveLinksNew);

        logger.info("Found {} linked resources of '{}.{}' resource", linkedResources.size(), resNamespace,
                resourceName);

        return linkedResources;
    }

    protected abstract void refreshQueues(Th2CustomResource resource);

    protected abstract void refreshMqLinks(
            List<Th2Link> linkResources,
            List<EnqueuedLink> activeLinks,
            Th2CustomResource... newResources
    );

    protected abstract void refreshGrpcLinks(
            List<Th2Link> linkResources,
            List<PinCouplingGRPC> activeLinks,
            Th2CustomResource... newResources
    );

    protected abstract void refreshDictionaryLinks(
            List<Th2Link> linkResources,
            List<DictionaryBinding> activeLinks,
            Th2CustomResource... newResources
    );

    protected abstract List<Th2CustomResource> getLinkedResources(
            Th2CustomResource resource,
            List<PinCoupling> oldLinks,
            List<PinCoupling> newLinks
    );
}
