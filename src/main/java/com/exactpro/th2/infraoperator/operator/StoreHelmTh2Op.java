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

package com.exactpro.th2.infraoperator.operator;

import com.exactpro.th2.infraoperator.OperatorState;
import com.exactpro.th2.infraoperator.operator.context.HelmOperatorContext;
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.exactpro.th2.infraoperator.util.ExtractUtils.*;

public abstract class StoreHelmTh2Op<CR extends Th2CustomResource> extends HelmReleaseTh2Op<CR> {

    private static final Logger logger = LoggerFactory.getLogger(StoreHelmTh2Op.class);

    public static final String EVENT_STORAGE_PIN_ALIAS = "estore-pin";

    public static final String EVENT_STORAGE_LINK_NAME_SUFFIX = "-to-estore_hidden-link";

    public static final String EVENT_ST_LINK_RESOURCE_NAME = "estore-hidden-links";

    public static final String EVENT_STORAGE_BOX_ALIAS = "estore";

    public static final String MESSAGE_STORAGE_PIN_ALIAS = "mstore-pin";

    public static final String MESSAGE_STORAGE_LINK_NAME_SUFFIX = "-to-mstore_hidden-link";

    public static final String MSG_ST_LINK_RESOURCE_NAME = "mstore-hidden-links";

    public static final String MESSAGE_STORAGE_BOX_ALIAS = "mstore";

    public StoreHelmTh2Op(HelmOperatorContext.Builder<?, ?> builder) {
        super(builder);
    }

    private void nameCheck(CR resource) {
        var msNamespace = extractNamespace(resource);
        var lock = OperatorState.INSTANCE.getLock(msNamespace);

        try {
            lock.lock();

            var msName = extractName(resource);
            var stName = getStorageName();

            if (!msName.equals(stName)) {

                var msg = String.format("%s<%s.%s> has an invalid name, must be '%s'",
                        extractType(resource), msNamespace, msName, stName);

                logger.warn(msg);
                resource.getStatus().failed(msg);
                updateStatus(resource);
                return;
            }
            super.addedEvent(resource);

        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void addedEvent(CR resource) {
        nameCheck(resource);
    }

    @Override
    protected void modifiedEvent(CR resource) {
        nameCheck(resource);
    }

    @Override
    protected void updateEventStorageLinksBeforeAdd(CR resource) {
        // nothing
    }

    @Override
    protected void updateMsgStorageLinksBeforeAdd(CR resource) {
        // nothing
    }

    @Override
    protected void updateEventStorageLinksAfterDelete(CR resource) {
        // nothing
    }

    @Override
    protected void updateMsgStorageLinksAfterDelete(CR resource) {
        // nothing
    }

    protected abstract String getStorageName();

}
