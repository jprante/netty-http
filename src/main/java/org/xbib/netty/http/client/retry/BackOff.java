package org.xbib.netty.http.client.retry;

import java.io.IOException;

/**
 * Back-off policy when retrying an operation.
 */
public interface BackOff {

    /**
     * Indicates that no more retries should be made for use in {@link #nextBackOffMillis()}. */
    long STOP = -1L;

    /**
     * Reset to initial state.
     */
    void reset() throws IOException;

    /**
     * Gets the number of milliseconds to wait before retrying the operation or {@link #STOP} to
     * indicate that no retries should be made.
     *
     * <p>
     * Example usage:
     * </p>
     *
     * <pre>
     long backOffMillis = backoff.nextBackOffMillis();
     if (backOffMillis == Backoff.STOP) {
     // do not retry operation
     } else {
     // sleep for backOffMillis milliseconds and retry operation
     }
     * </pre>
     */
    long nextBackOffMillis() throws IOException;

    /**
     * Fixed back-off policy whose back-off time is always zero, meaning that the operation is retried
     * immediately without waiting.
     */
    BackOff ZERO_BACKOFF = new BackOff() {

        public void reset() {
        }

        public long nextBackOffMillis() {
            return 0;
        }
    };

    /**
     * Fixed back-off policy that always returns {@code #STOP} for {@link #nextBackOffMillis()},
     * meaning that the operation should not be retried.
     */
    BackOff STOP_BACKOFF = new BackOff() {

        public void reset() {
        }

        public long nextBackOffMillis() {
            return STOP;
        }
    };
}
