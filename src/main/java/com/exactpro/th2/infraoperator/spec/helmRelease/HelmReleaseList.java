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

package com.exactpro.th2.infraoperator.spec.helmRelease;

import io.fabric8.kubernetes.client.CustomResourceList;

/*
    TODO: Delete this class
    Currently this can't be deleted since fabric8 has partially deleted
    usage of List classes, there are some places where it is being used
    Current usages of lists:
       client.customResources()
       MixedOperation (T, L, Operation)
 */
public class HelmReleaseList extends CustomResourceList<HelmRelease> {

}

