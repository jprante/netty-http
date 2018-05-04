package org.xbib.netty.http.client.retry;

/**
 * Implementation of {@link BackOff} that increases the back off period for each retry attempt using
 * a randomization function that grows exponentially.
 *
 * <p>
 * {@link #nextBackOffMillis()} is calculated using the following formula:
 * </p>
 *
 * <pre>
 randomized_interval =
 retry_interval * (random value in range [1 - randomization_factor, 1 + randomization_factor])
 * </pre>
 *
 * <p>
 * In other words {@link #nextBackOffMillis()} will range between the randomization factor
 * percentage below and above the retry interval. For example, using 2 seconds as the base retry
 * interval and 0.5 as the randomization factor, the actual back off period used in the next retry
 * attempt will be between 1 and 3 seconds.
 * </p>
 *
 * <p>
 * <b>Note:</b> max_interval caps the retry_interval and not the randomized_interval.
 * </p>
 *
 * <p>
 * If the time elapsed since an {@link ExponentialBackOff} instance is created goes past the
 * max_elapsed_time then the method {@link #nextBackOffMillis()} starts returning
 * {@link BackOff#STOP}. The elapsed time can be reset by calling {@link #reset()}.
 * </p>
 *
 * <p>
 * Example: The default retry_interval is .5 seconds, default randomization_factor is 0.5, default
 * multiplier is 1.5 and the default max_interval is 1 minute. For 10 tries the sequence will be
 * (values in seconds) and assuming we go over the max_elapsed_time on the 10th try:
 * </p>
 *
 * <pre>
 request#     retry_interval     randomized_interval

 1             0.5                [0.25,   0.75]
 2             0.75               [0.375,  1.125]
 3             1.125              [0.562,  1.687]
 4             1.687              [0.8435, 2.53]
 5             2.53               [1.265,  3.795]
 6             3.795              [1.897,  5.692]
 7             5.692              [2.846,  8.538]
 8             8.538              [4.269, 12.807]
 9            12.807              [6.403, 19.210]
 10           19.210              {@link BackOff#STOP}
 * </pre>
 *
 * <p>
 * Implementation is not thread-safe.
 * </p>
 */
public class ExponentialBackOff implements BackOff {

    /** The default initial interval value in milliseconds (0.5 seconds). */
    public static final int DEFAULT_INITIAL_INTERVAL_MILLIS = 500;

    /**
     * The default randomization factor (0.5 which results in a random period ranging between 50%
     * below and 50% above the retry interval).
     */
    public static final double DEFAULT_RANDOMIZATION_FACTOR = 0.5;

    /** The default multiplier value (1.5 which is 50% increase per back off). */
    public static final double DEFAULT_MULTIPLIER = 1.5;

    /** The default maximum back off time in milliseconds (1 minute). */
    public static final int DEFAULT_MAX_INTERVAL_MILLIS = 60000;

    /** The default maximum elapsed time in milliseconds (15 minutes). */
    public static final int DEFAULT_MAX_ELAPSED_TIME_MILLIS = 900000;

    /** The current retry interval in milliseconds. */
    private int currentIntervalMillis;

    /** The initial retry interval in milliseconds. */
    private final int initialIntervalMillis;

    /**
     * The randomization factor to use for creating a range around the retry interval.
     *
     * <p>
     * A randomization factor of 0.5 results in a random period ranging between 50% below and 50%
     * above the retry interval.
     * </p>
     */
    private final double randomizationFactor;

    /** The value to multiply the current interval with for each retry attempt. */
    private final double multiplier;

    /**
     * The maximum value of the back off period in milliseconds. Once the retry interval reaches this
     * value it stops increasing.
     */
    private final int maxIntervalMillis;

    /**
     * The system time in nanoseconds. It is calculated when an ExponentialBackOffPolicy instance is
     * created and is reset when {@link #reset()} is called.
     */
    private long startTimeNanos;

    /**
     * The maximum elapsed time after instantiating {@link ExponentialBackOff} or calling
     * {@link #reset()} after which {@link #nextBackOffMillis()} returns {@link BackOff#STOP}.
     */
    private final int maxElapsedTimeMillis;

    /** Nano clock. */
    private final NanoClock nanoClock;

    /**
     * Creates an instance of ExponentialBackOffPolicy using default values.
     *
     * <p>
     * To override the defaults use {@link Builder}.
     * </p>
     *
     * <ul>
     * <li>{@code initialIntervalMillis} defaults to {@link #DEFAULT_INITIAL_INTERVAL_MILLIS}</li>
     * <li>{@code randomizationFactor} defaults to {@link #DEFAULT_RANDOMIZATION_FACTOR}</li>
     * <li>{@code multiplier} defaults to {@link #DEFAULT_MULTIPLIER}</li>
     * <li>{@code maxIntervalMillis} defaults to {@link #DEFAULT_MAX_INTERVAL_MILLIS}</li>
     * <li>{@code maxElapsedTimeMillis} defaults in {@link #DEFAULT_MAX_ELAPSED_TIME_MILLIS}</li>
     * </ul>
     */
    public ExponentialBackOff() {
        this(new Builder());
    }

    /**
     * @param builder builder
     */
    private ExponentialBackOff(Builder builder) {
        initialIntervalMillis = builder.initialIntervalMillis;
        randomizationFactor = builder.randomizationFactor;
        multiplier = builder.multiplier;
        maxIntervalMillis = builder.maxIntervalMillis;
        maxElapsedTimeMillis = builder.maxElapsedTimeMillis;
        nanoClock = builder.nanoClock;
        reset();
    }

    /**
     * Sets the interval back to the initial retry interval and restarts the timer.
     */
    public final void reset() {
        currentIntervalMillis = initialIntervalMillis;
        startTimeNanos = nanoClock.nanoTime();
    }

    public void setStartTimeNanos(long startTimeNanos) {
        this.startTimeNanos = startTimeNanos;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * This method calculates the next back off interval using the formula: randomized_interval =
     * retry_interval +/- (randomization_factor * retry_interval)
     * </p>
     *
     * <p>
     * Subclasses may override if a different algorithm is required.
     * </p>
     */
    public long nextBackOffMillis() {
        // Make sure we have not gone over the maximum elapsed time.
        if (getElapsedTimeMillis() > maxElapsedTimeMillis) {
            return STOP;
        }
        int randomizedInterval =
                getRandomValueFromInterval(randomizationFactor, Math.random(), currentIntervalMillis);
        incrementCurrentInterval();
        return randomizedInterval;
    }

    /**
     * Returns a random value from the interval [randomizationFactor * currentInterval,
     * randomizationFactor * currentInterval].
     * @param randomizationFactor the randomization factor
     * @param random scaling factor
     * @param currentIntervalMillis milliseconds
     * @return random value
     */
    public static int getRandomValueFromInterval(double randomizationFactor, double random, int currentIntervalMillis) {
        double delta = randomizationFactor * currentIntervalMillis;
        double minInterval = currentIntervalMillis - delta;
        double maxInterval = currentIntervalMillis + delta;
        // Get a random value from the range [minInterval, maxInterval].
        // The formula used below has a +1 because if the minInterval is 1 and the maxInterval is 3 then
        // we want a 33% chance for selecting either 1, 2 or 3.
        return (int) (minInterval + (random * (maxInterval - minInterval + 1)));
    }

    /**
     * Returns the initial retry interval in milliseconds.
     * @return interval milliseconds
     */
    public final int getInitialIntervalMillis() {
        return initialIntervalMillis;
    }

    /**
     * Returns the randomization factor to use for creating a range around the retry interval.
     * @return randomization factor
     * <p>
     * A randomization factor of 0.5 results in a random period ranging between 50% below and 50%
     * above the retry interval.
     * </p>
     */
    public final double getRandomizationFactor() {
        return randomizationFactor;
    }

    /**
     * Returns the current retry interval in milliseconds.
     * @return current interval in milliseconds
     */
    public final int getCurrentIntervalMillis() {
        return currentIntervalMillis;
    }

    /**
     * Returns the value to multiply the current interval with for each retry attempt.
     * @return multiplier
     */
    public final double getMultiplier() {
        return multiplier;
    }

    /**
     * Returns the maximum value of the back off period in milliseconds. Once the current interval
     * reaches this value it stops increasing.
     * @return maximum interval value in milliseconds
     */
    public final int getMaxIntervalMillis() {
        return maxIntervalMillis;
    }

    /**
     * Returns the maximum elapsed time in milliseconds.
     * @return maximum elapsed time in milliseconds
     * <p>
     * If the time elapsed since an {@link ExponentialBackOff} instance is created goes past the
     * max_elapsed_time then the method {@link #nextBackOffMillis()} starts returning
     * {@link BackOff#STOP}. The elapsed time can be reset by calling {@link #reset()}.
     * </p>
     */
    public final int getMaxElapsedTimeMillis() {
        return maxElapsedTimeMillis;
    }

    /**
     * Returns the elapsed time in milliseconds since an {@link ExponentialBackOff} instance is
     * created and is reset when {@link #reset()} is called.
     * @return the elapsed time in milliseconds
     * <p>
     * The elapsed time is computed using {@link System#nanoTime()}.
     * </p>
     */
    public final long getElapsedTimeMillis() {
        return (nanoClock.nanoTime() - startTimeNanos) / 1000000;
    }

    /**
     * Increments the current interval by multiplying it with the multiplier.
     */
    private void incrementCurrentInterval() {
        // Check for overflow, if overflow is detected set the current interval to the max interval.
        if (currentIntervalMillis >= maxIntervalMillis / multiplier) {
            currentIntervalMillis = maxIntervalMillis;
        } else {
            currentIntervalMillis *= multiplier;
        }
    }

    /**
     * Nano clock which can be used to measure elapsed time in nanoseconds.
     *
     * <p>
     * The default system implementation can be accessed at {@link #SYSTEM}. Alternative implementations
     * may be used for testing.
     * </p>
     *
     */
    public interface NanoClock {

        /**
         * Returns the current value of the most precise available system timer, in nanoseconds for use to
         * measure elapsed time, to match the behavior of {@link System#nanoTime()}.
         * @return value of timer in nanoseconds
         */
        long nanoTime();

        /**
         * Provides the default System implementation of a nano clock by using {@link System#nanoTime()}.
         */
        NanoClock SYSTEM = System::nanoTime;
    }

    /**
     * Builder for {@link ExponentialBackOff}.
     *
     * <p>
     * Implementation is not thread-safe.
     * </p>
     */
    public static class Builder {

        /** The initial retry interval in milliseconds. */
        private int initialIntervalMillis = DEFAULT_INITIAL_INTERVAL_MILLIS;

        /**
         * The randomization factor to use for creating a range around the retry interval.
         *
         * <p>
         * A randomization factor of 0.5 results in a random period ranging between 50% below and 50%
         * above the retry interval.
         * </p>
         */
        private double randomizationFactor = DEFAULT_RANDOMIZATION_FACTOR;

        /**
         * The value to multiply the current interval with for each retry attempt.
         */
        private double multiplier = DEFAULT_MULTIPLIER;

        /**
         * The maximum value of the back off period in milliseconds. Once the retry interval reaches
         * this value it stops increasing.
         */
        private int maxIntervalMillis = DEFAULT_MAX_INTERVAL_MILLIS;

        /**
         * The maximum elapsed time in milliseconds after instantiating {@link ExponentialBackOff} or
         * calling {@link #reset()} after which {@link #nextBackOffMillis()} returns
         * {@link BackOff#STOP}.
         */
        private int maxElapsedTimeMillis = DEFAULT_MAX_ELAPSED_TIME_MILLIS;

        /**
         * Nano clock.
         */
        private NanoClock nanoClock = NanoClock.SYSTEM;

        public Builder() {
        }

        /**
         * Builds a new instance of {@link ExponentialBackOff}.
         * @return an {@link ExponentialBackOff} instance
         */
        public ExponentialBackOff build() {
            if (initialIntervalMillis <= 0) {
                throw new IllegalArgumentException();
            }
            if (!(0 <= randomizationFactor && randomizationFactor < 1)) {
                throw new IllegalArgumentException();
            }
            if (multiplier < 1) {
                throw new IllegalArgumentException();
            }
            if ((maxIntervalMillis < initialIntervalMillis)) {
                throw new IllegalArgumentException();
            }
            if (maxElapsedTimeMillis <= 0) {
                throw new IllegalArgumentException();
            }
            return new ExponentialBackOff(this);
        }

        /**
         * Sets the initial retry interval in milliseconds. The default value is
         * {@link #DEFAULT_INITIAL_INTERVAL_MILLIS}. Must be {@code > 0}.
         * @param initialIntervalMillis interval milliseconds
         * @return the builder
         *
         * <p>
         * Overriding is only supported for the purpose of calling the super implementation and changing
         * the return type, but nothing else.
         * </p>
         */
        public Builder setInitialIntervalMillis(int initialIntervalMillis) {
            this.initialIntervalMillis = initialIntervalMillis;
            return this;
        }

        /**
         * Sets the randomization factor to use for creating a range around the retry interval. The
         * default value is {@link #DEFAULT_RANDOMIZATION_FACTOR}. Must fall in the range
         * {@code 0 <= randomizationFactor < 1}.
         * @param randomizationFactor the randomization factor
         * @return the builder
         *
         * <p>
         * A randomization factor of 0.5 results in a random period ranging between 50% below and 50%
         * above the retry interval.
         * </p>
         *
         * <p>
         * Overriding is only supported for the purpose of calling the super implementation and changing
         * the return type, but nothing else.
         * </p>
         */
        public Builder setRandomizationFactor(double randomizationFactor) {
            this.randomizationFactor = randomizationFactor;
            return this;
        }

        /**
         * Sets the value to multiply the current interval with for each retry attempt. The default
         * value is {@link #DEFAULT_MULTIPLIER}. Must be {@code >= 1}.
         * @param multiplier the multiplier
         * @return the builder
         *
         * <p>
         * Overriding is only supported for the purpose of calling the super implementation and changing
         * the return type, but nothing else.
         * </p>
         */
        public Builder setMultiplier(double multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        /**
         * Sets the maximum value of the back off period in milliseconds. Once the current interval
         * reaches this value it stops increasing. The default value is
         * {@link #DEFAULT_MAX_INTERVAL_MILLIS}.
         * @param maxIntervalMillis maximum interval in miliseconds
         * @return the builder
         *
         * <p>
         * Overriding is only supported for the purpose of calling the super implementation and changing
         * the return type, but nothing else.
         * </p>
         */
        public Builder setMaxIntervalMillis(int maxIntervalMillis) {
            this.maxIntervalMillis = maxIntervalMillis;
            return this;
        }

        /**
         * Sets the maximum elapsed time in milliseconds. The default value is
         * {@link #DEFAULT_MAX_ELAPSED_TIME_MILLIS}. Must be {@code > 0}.
         * @param maxElapsedTimeMillis maximum elapsed time millis
         * @return the builder
         *
         * <p>
         * If the time elapsed since an {@link ExponentialBackOff} instance is created goes past the
         * max_elapsed_time then the method {@link #nextBackOffMillis()} starts returning
         * {@link BackOff#STOP}. The elapsed time can be reset by calling {@link #reset()}.
         * </p>
         *
         * <p>
         * Overriding is only supported for the purpose of calling the super implementation and changing
         * the return type, but nothing else.
         * </p>
         */
        public Builder setMaxElapsedTimeMillis(int maxElapsedTimeMillis) {
            this.maxElapsedTimeMillis = maxElapsedTimeMillis;
            return this;
        }

        /**
         * Sets the nano clock ({@link NanoClock#SYSTEM} by default).
         * @param nanoClock the nano clock
         * @return the builder
         * <p>
         * Overriding is only supported for the purpose of calling the super implementation and changing
         * the return type, but nothing else.
         * </p>
         */
        public Builder setNanoClock(NanoClock nanoClock) {
            if (nanoClock != null) {
                this.nanoClock = nanoClock;
            }
            return this;
        }
    }
}
