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

package io.reactivex.netty.internal;

import io.netty.channel.Channel;
import rx.functions.Action0;

public abstract class ExecuteInEventloopAction implements Action0, Runnable {

    private final Channel channel;

    protected ExecuteInEventloopAction(Channel channel) {
        this.channel = channel;
    }

    @Override
    public void call() {
        if (channel.eventLoop().inEventLoop()) {
            run();
        } else {
            channel.eventLoop().execute(this);
        }
    }
}
