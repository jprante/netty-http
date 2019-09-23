package org.xbib.netty.http.client.test.retry;

import org.junit.jupiter.api.Test;
import org.xbib.netty.http.client.api.BackOff;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link MockBackOff}.
 */
class MockBackOffTest {

    @Test
    void testNextBackOffMillis() throws IOException {
        subtestNextBackOffMillis(0, new MockBackOff());
        subtestNextBackOffMillis(BackOff.STOP, new MockBackOff().setBackOffMillis(BackOff.STOP));
        subtestNextBackOffMillis(42, new MockBackOff().setBackOffMillis(42));
    }

    private void subtestNextBackOffMillis(long expectedValue, BackOff backOffPolicy) throws IOException {
        for (int i = 0; i < 10; i++) {
            assertEquals(expectedValue, backOffPolicy.nextBackOffMillis());
        }
    }
}
