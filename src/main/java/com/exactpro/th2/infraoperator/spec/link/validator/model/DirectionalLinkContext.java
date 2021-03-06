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

package com.exactpro.th2.infraoperator.spec.link.validator.model;

import com.exactpro.th2.infraoperator.spec.shared.BoxDirection;
import com.exactpro.th2.infraoperator.spec.shared.SchemaConnectionType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class DirectionalLinkContext {

    private String linkName;

    private String boxName;

    private String boxPinName;

    private String linksSectionName;

    private BoxDirection boxDirection;

    private SchemaConnectionType connectionType;

    private String routingStrategy;

    private String linkResName;

    private String linkNamespace;

}
