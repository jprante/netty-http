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
package org.xbib.netty.http.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.FixedChannelPool;

/**
 *
 */
public class HttpClientChannelPoolMap extends AbstractChannelPoolMap<InetAddressKey, FixedChannelPool> {

    private final HttpClient httpClient;

    private final HttpClientChannelContext httpClientChannelContext;

    private final Bootstrap bootstrap;

    private final int maxConnections;

    private HttpClientChannelInitializer httpClientChannelInitializer;

    private HttpClientChannelPoolHandler httpClientChannelPoolHandler;

    HttpClientChannelPoolMap(HttpClient httpClient,
                             HttpClientChannelContext httpClientChannelContext,
                             Bootstrap bootstrap,
                             int maxConnections) {
        this.httpClient = httpClient;
        this.httpClientChannelContext = httpClientChannelContext;
        this.bootstrap = bootstrap;
        this.maxConnections = maxConnections;
    }

    @Override
    protected FixedChannelPool newPool(InetAddressKey key) {
        this.httpClientChannelInitializer = new HttpClientChannelInitializer(httpClientChannelContext,
                new Http1Handler(httpClient), new Http2Handler(httpClient));
        this.httpClientChannelPoolHandler = new HttpClientChannelPoolHandler(httpClientChannelInitializer, key);
        return new FixedChannelPool(bootstrap.remoteAddress(key.getInetSocketAddress()),
               httpClientChannelPoolHandler, maxConnections);
    }

    public HttpClientChannelInitializer getHttpClientChannelInitializer() {
        return httpClientChannelInitializer;
    }

    public HttpClientChannelPoolHandler getHttpClientChannelPoolHandler() {
        return httpClientChannelPoolHandler;
    }
}
