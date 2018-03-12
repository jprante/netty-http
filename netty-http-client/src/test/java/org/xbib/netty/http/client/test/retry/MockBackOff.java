package org.xbib.netty.http.client.test.retry;

import org.xbib.netty.http.client.retry.BackOff;

import java.io.IOException;

/**
 * Mock for {@link BackOff} that always returns a fixed number.
 *
 * <p>
 * Implementation is not thread-safe.
 * </p>
 *
 */
public class MockBackOff implements BackOff {

    /** Fixed back-off milliseconds. */
    private long backOffMillis;

    /** Maximum number of tries before returning {@link #STOP}. */
    private int maxTries = 10;

    /** Number of tries so far. */
    private int numTries;

    @Override
    public void reset() {
        numTries = 0;
    }

    @Override
    public long nextBackOffMillis() {
        if (numTries >= maxTries || backOffMillis == STOP) {
            return STOP;
        }
        numTries++;
        return backOffMillis;
    }

    /**
     * Sets the fixed back-off milliseconds (defaults to {@code 0}).
     *
     * <p>
     * Overriding is only supported for the purpose of calling the super implementation and changing
     * the return type, but nothing else.
     * </p>
     */
    public MockBackOff setBackOffMillis(long backOffMillis) {
        //Preconditions.checkArgument(backOffMillis == STOP || backOffMillis >= 0);
        this.backOffMillis = backOffMillis;
        return this;
    }

    /**
     * Sets the maximum number of tries before returning {@link #STOP} (defaults to {@code 10}).
     *
     * <p>
     * Overriding is only supported for the purpose of calling the super implementation and changing
     * the return type, but nothing else.
     * </p>
     */
    public MockBackOff setMaxTries(int maxTries) {
        //Preconditions.checkArgument(maxTries >= 0);
        this.maxTries = maxTries;
        return this;
    }

    /** Returns the maximum number of tries before returning {@link #STOP}. */
    public final int getMaxTries() {
        return numTries;
    }

    /** Returns the number of tries so far. */
    public final int getNumberOfTries() {
        return numTries;
    }
}
