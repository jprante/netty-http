/*
 * Copyright 2017 Jörg Prante
 *
 * Jörg Prante licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.xbib.netty.http.client.internal;

import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.socket.SocketChannel;
import org.xbib.netty.http.client.handler.HttpClientChannelInitializer;
import org.xbib.netty.http.client.util.InetAddressKey;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class HttpClientChannelPoolHandler implements ChannelPoolHandler {

    private static final Logger logger = Logger.getLogger(HttpClientChannelPoolHandler.class.getName());

    private final HttpClientChannelInitializer channelInitializer;

    private final InetAddressKey key;

    private final AtomicInteger active = new AtomicInteger();

    private int peak;

    public HttpClientChannelPoolHandler(HttpClientChannelInitializer channelInitializer, InetAddressKey key) {
        this.channelInitializer = channelInitializer;
        this.key = key;
    }

    @Override
    public void channelCreated(Channel ch) throws Exception {
        logger.log(Level.FINE, () -> "channel created " + ch + " key:" + key);
        channelInitializer.initChannel((SocketChannel) ch, key);
        int n = active.incrementAndGet();
        if (n > peak) {
            peak = n;
        }
    }

    @Override
    public void channelAcquired(Channel ch) throws Exception {
        logger.log(Level.FINE, () -> "channel acquired from pool " + ch);
    }

    @Override
    public void channelReleased(Channel ch) throws Exception {
        logger.log(Level.FINE, () -> "channel released to pool " + ch);
        active.decrementAndGet();
    }

    public int getActive() {
        return active.get();
    }

    public int getPeak() {
        return peak;
    }
}
