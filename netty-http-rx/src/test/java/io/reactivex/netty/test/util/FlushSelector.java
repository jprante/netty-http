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
package io.reactivex.netty.test.util;

import rx.functions.Func1;

public class FlushSelector<T> implements Func1<T, Boolean> {

    private final int flushEvery;
    private int count;

    public FlushSelector(int flushEvery) {
        this.flushEvery = flushEvery;
    }

    @Override
    public Boolean call(T o) {
        return ++count % flushEvery == 0;
    }

    public int getFlushEvery() {
        return flushEvery;
    }
}
