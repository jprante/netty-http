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
package io.reactivex.netty.channel;

import io.netty.channel.Channel;
import rx.Subscriber;

/**
 * An event to communicate the subscriber of a new channel created by {@link AbstractConnectionToChannelBridge}.
 *
 * <h2>Connection reuse</h2>
 *
 * For cases, where the {@link Connection} is pooled, reuse should be indicated explicitly via
 * {@link ConnectionInputSubscriberResetEvent}. There can be multiple {@link ConnectionInputSubscriberResetEvent}s
 * sent to the same channel and hence the same instance of {@link AbstractConnectionToChannelBridge}.
 *
 * @param <R> Type read from the connection held by the event.
 * @param <W> Type written to the connection held by the event.
 */
public class ChannelSubscriberEvent<R, W> {

    private final Subscriber<? super Channel> subscriber;

    public ChannelSubscriberEvent(Subscriber<? super Channel> subscriber) {
        this.subscriber = subscriber;
    }

    public Subscriber<? super Channel> getSubscriber() {
        return subscriber;
    }
}
