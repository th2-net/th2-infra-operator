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

import com.exactpro.th2.infraoperator.spec.strategy.linkresolver.queue.QueueName;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QueueNameTests {

    @Test
    public void testFormat() {
        Assertions.assertEquals("link[namespace:box:pin]", QueueName.format("namespace", "box", "pin"));
    }


    @Test
    public void invalidQueueNameTest() {
        String[] tests = new String[] {
                "tes",
                "link",
                "link[]",
                "link[::]",
                "link[q::]",
                "link[:w:]",
                "link[q::w]",
                "link[q:wds:0] ",
                "link[namespace:wds: 0]",
                "link[name_space:wds:pin]",
                "link[q:wd_s:ab",
                "link[q:wds:ABA]",
                "link[q:-:0]",
                "link[qhj:w ds:0]",
                "link [q:wds:0]"
        };

        for (String test: tests)
            assertEquals(null, QueueName.fromString(test));
    }


    @Test
    public void validQueueNameTest() {

        String[] tests = new String[] {
                "",
                "link[abc:030:test]",
                "link[name-space:box:pin]",
                "link[schema-demo:test-box:ab]",
                "link[q:wds:pin_cd]",
                "link[q:wds:pin-cd]",
                "link[0:1:ab]"
        };

        for (String test: tests) {
            var res = QueueName.fromString(test);
            assertEquals(test, res == null ? null : res.toString());
        }
    }
}
