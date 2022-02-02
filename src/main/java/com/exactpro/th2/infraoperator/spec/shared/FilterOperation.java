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

package com.exactpro.th2.infraoperator.spec.shared;

import java.util.Arrays;

public enum FilterOperation {

    EQUAL(0),
    NOT_EQUAL(1),
    EMPTY(2),
    NOT_EMPTY(3),
    WILDCARD(4);

    private int id;

    FilterOperation(int id) {
        this.id = id;
    }

    public static FilterOperation of(int id) {
        return Arrays.stream(values())
                .filter(op -> op.id == id)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unknown operation id '" + id + "'"));
    }

}