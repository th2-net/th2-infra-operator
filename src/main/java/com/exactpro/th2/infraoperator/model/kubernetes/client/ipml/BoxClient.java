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
import com.exactpro.th2.infraoperator.spec.box.DoneableTh2Box;
import com.exactpro.th2.infraoperator.spec.box.Th2Box;
import com.exactpro.th2.infraoperator.spec.box.Th2BoxList;
import io.fabric8.kubernetes.client.KubernetesClient;

public class BoxClient extends DefaultResourceClient<Th2Box> {

    public BoxClient(KubernetesClient client) {
        super(
                client,
                Th2Box.class,
                Th2BoxList.class,
                DoneableTh2Box.class,
                "th2boxes.th2.exactpro.com"
        );
    }

}
