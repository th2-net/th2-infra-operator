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

package com.exactpro.th2.infraoperator.model.kubernetes.client.impl;

import com.exactpro.th2.infraoperator.model.kubernetes.client.DefaultResourceClient;
import com.exactpro.th2.infraoperator.spec.job.Th2Job;
import io.fabric8.kubernetes.client.KubernetesClient;

public class JobClient extends DefaultResourceClient<Th2Job> {

    public JobClient(KubernetesClient client) {
        super(
                client,
                Th2Job.class,
                "th2jobs.th2.exactpro.com"
        );
    }

}
