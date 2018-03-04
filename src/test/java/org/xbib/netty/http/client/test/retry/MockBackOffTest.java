package org.xbib.netty.http.client.test.retry;

import org.junit.Test;
import org.xbib.netty.http.client.retry.BackOff;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Tests {@link MockBackOff}.
 */
public class MockBackOffTest {

    @Test
    public void testNextBackOffMillis() throws IOException {
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
