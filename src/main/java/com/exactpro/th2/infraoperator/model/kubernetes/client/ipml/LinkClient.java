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

package com.exactpro.th2.infraoperator.model.kubernetes.client.ipml;

import com.exactpro.th2.infraoperator.model.kubernetes.client.DefaultResourceClient;
import com.exactpro.th2.infraoperator.spec.link.DoneableTh2Link;
import com.exactpro.th2.infraoperator.spec.link.Th2Link;
import com.exactpro.th2.infraoperator.spec.link.Th2LinkList;
import io.fabric8.kubernetes.client.KubernetesClient;

public class LinkClient extends DefaultResourceClient<Th2Link> {

    public LinkClient(KubernetesClient client) {
        super(
                client,
                Th2Link.class,
                Th2LinkList.class,
                DoneableTh2Link.class,
                "th2links.th2.exactpro.com"
        );
    }

}
