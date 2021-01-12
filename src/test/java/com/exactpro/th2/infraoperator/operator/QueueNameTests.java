package com.exactpro.th2.infraoperator.operator;

import com.exactpro.th2.infraoperator.spec.strategy.linkResolver.queue.QueueName;
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
