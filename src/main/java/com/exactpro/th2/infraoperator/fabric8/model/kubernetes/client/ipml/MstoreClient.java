/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.ipml;

import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.DefaultResourceClient;
import com.exactpro.th2.infraoperator.fabric8.spec.mstore.DoneableTh2Mstore;
import com.exactpro.th2.infraoperator.fabric8.spec.mstore.Th2Mstore;
import com.exactpro.th2.infraoperator.fabric8.spec.mstore.Th2MstoreList;
import io.fabric8.kubernetes.client.KubernetesClient;

public class MstoreClient extends DefaultResourceClient<Th2Mstore> {

    public MstoreClient(KubernetesClient client) {
        super(
                client,
                Th2Mstore.class,
                Th2MstoreList.class,
                DoneableTh2Mstore.class,
                "th2messagestores.th2.exactpro.com"
        );
    }

}
