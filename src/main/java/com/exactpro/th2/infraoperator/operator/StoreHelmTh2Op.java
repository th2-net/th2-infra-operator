/*
 * Copyright 2020-2024 Exactpro (Exactpro Systems Limited)
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
import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.exactpro.th2.infraoperator.util.ExtractUtils.*;

public abstract class StoreHelmTh2Op<CR extends Th2CustomResource> extends HelmReleaseTh2Op<CR> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoreHelmTh2Op.class);

    public StoreHelmTh2Op(KubernetesClient client) {
        super(client);
    }

    private void nameCheck(CR resource) throws IOException {
        var msNamespace = extractNamespace(resource);
        var lock = OperatorState.INSTANCE.getLock(msNamespace);

        lock.lock();
        try {
            var msName = extractName(resource);
            var stName = getStorageName();

            if (!msName.equals(stName)) {

                var msg = String.format("%s<%s.%s> has an invalid name, must be '%s'",
                        extractType(resource), msNamespace, msName, stName);

                LOGGER.warn(msg);
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
    protected void addedEvent(CR resource) throws IOException {
        nameCheck(resource);
    }

    @Override
    protected void modifiedEvent(CR resource) throws IOException {
        nameCheck(resource);
    }

    protected abstract String getStorageName();

}
