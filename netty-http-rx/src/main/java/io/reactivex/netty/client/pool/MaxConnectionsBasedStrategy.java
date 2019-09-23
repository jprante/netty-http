/*
 * Copyright 2015 Netflix, Inc.
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
 *
 */

package io.reactivex.netty.client.pool;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An implementation of {@link PoolLimitDeterminationStrategy} that limits the pool based on a maximum connections limit.
 * This limit can be increased or decreased at runtime.
 */
public class MaxConnectionsBasedStrategy implements PoolLimitDeterminationStrategy {

    public static final int DEFAULT_MAX_CONNECTIONS = 1000;

    private final AtomicInteger limitEnforcer;
    private final AtomicInteger maxConnections;

    public MaxConnectionsBasedStrategy() {
        this(DEFAULT_MAX_CONNECTIONS);
    }

    public MaxConnectionsBasedStrategy(int maxConnections) {
        this.maxConnections = new AtomicInteger(maxConnections);
        limitEnforcer = new AtomicInteger();
    }

    @Override
    public boolean acquireCreationPermit(long acquireStartTime, TimeUnit timeUnit) {
        /**
         * As opposed to limitEnforcer.incrementAndGet() we follow this model as this does not change the limitEnforcer
         * value unless there are enough permits.
         * If we were to use incrementAndGet(), in case of overflow (from max allowed limit) we would have to decrement
         * the limitEnforcer. This may show temporary overflows in getMaxConnections() which may be disturbing for a
         * user. However, even if we use incrementAndGet() the counter corrects itself over time.
         * This is just a more semantically correct implementation with similar performance characterstics as
         * incrementAndGet()
         */
        for (;;) {
            final int currentValue = limitEnforcer.get();
            final int newValue = currentValue + 1;
            final int maxAllowedConnections = maxConnections.get();
            if (newValue <= maxAllowedConnections) {
                if (limitEnforcer.compareAndSet(currentValue, newValue)) {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    public int incrementMaxConnections(int incrementBy) {
        return maxConnections.addAndGet(incrementBy);
    }

    public int decrementMaxConnections(int decrementBy) {
        return maxConnections.addAndGet(-1 * decrementBy);
    }

    public int getMaxConnections() {
        return maxConnections.get();
    }

    @Override
    public int getAvailablePermits() {
        return maxConnections.get() - limitEnforcer.get();
    }

    @Override
    public void releasePermit() {
        /**
         * As opposed to limitEnforcer.decrementAndGet() we follow this model as this does not change the limitEnforcer
         * value unless there are enough permits.
         * If we were to use decrementAndGet(), in case of overflow (from max allowed limit) we would have to decrement
         * the limitEnforcer. This may show temporary overflows in getMaxConnections() which may be disturbing for a
         * user. However, even if we use decrementAndGet() the counter corrects itself over time.
         * This is just a more semantically correct implementation with similar performance characterstics as
         * decrementAndGet()
         */
        for (;;) {
            final int currentValue = limitEnforcer.get();
            final int newValue = currentValue - 1;
            if (newValue >= 0) {
                if (!limitEnforcer.compareAndSet(currentValue, newValue)) {
                    continue;
                }
            }
            break;
        }
    }
}
