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

package io.reactivex.netty.client;

import io.reactivex.netty.channel.Connection;
import rx.Observable;

/**
 * A contract to control how connections are established from a client.
 *
 * @param <W> The type of objects written on the connections created by this provider.
 * @param <R> The type of objects read from the connections created by this provider.
 */
public interface ConnectionProvider<W, R> {

    /**
     * Returns an {@code Observable} that emits a single connection every time it is subscribed.
     *
     * @return An {@code Observable} that emits a single connection every time it is subscribed.
     */
    Observable<Connection<R, W>> newConnectionRequest();

}
