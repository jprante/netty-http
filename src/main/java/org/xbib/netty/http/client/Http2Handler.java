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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.pool.ChannelPool;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.internal.PlatformDependent;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Netty channel handler for HTTP/2 responses.
 */
@ChannelHandler.Sharable
public class Http2Handler extends SimpleChannelInboundHandler<FullHttpResponse> {

    private static final Logger logger = Logger.getLogger(Http2Handler.class.getName());

    private final Map<Integer, Entry<ChannelFuture, ChannelPromise>> streamidPromiseMap;

    private final HttpClient httpClient;

    Http2Handler(HttpClient httpClient) {
        this.streamidPromiseMap = PlatformDependent.newConcurrentHashMap();
        this.httpClient = httpClient;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse httpResponse) throws Exception {
        logger.log(Level.FINE, () -> httpResponse.getClass().getName());
        Integer streamId = httpResponse.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
        if (streamId == null) {
            logger.log(Level.WARNING, () -> "stream ID missing");
            return;
        }
        final HttpRequestContext httpRequestContext =
                ctx.channel().attr(HttpClientChannelContext.REQUEST_CONTEXT_ATTRIBUTE_KEY).get();
        Entry<ChannelFuture, ChannelPromise> entry = streamidPromiseMap.get(streamId);
        if (entry != null) {
            HttpResponseListener httpResponseListener =
                    ctx.channel().attr(HttpClientChannelContext.RESPONSE_LISTENER_ATTRIBUTE_KEY).get();
            if (httpResponseListener != null) {
                httpResponseListener.onResponse(httpResponse);
            }
            entry.getValue().setSuccess();
            if (httpClient.tryRedirect(ctx.channel(), httpResponse, httpRequestContext)) {
                return;
            }
            logger.log(Level.FINE, () -> "success");
            httpRequestContext.success("response arrived");
            final ChannelPool channelPool =
                    ctx.channel().attr(HttpClientChannelContext.CHANNEL_POOL_ATTRIBUTE_KEY).get();
            channelPool.release(ctx.channel());
        } else {
            logger.log(Level.WARNING, () -> "stream id not found in promises: " + streamId);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.log(Level.FINE, ctx::toString);
        final ChannelPool channelPool =
                ctx.channel().attr(HttpClientChannelContext.CHANNEL_POOL_ATTRIBUTE_KEY).get();
        channelPool.release(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ExceptionListener exceptionListener =
                ctx.channel().attr(HttpClientChannelContext.EXCEPTION_LISTENER_ATTRIBUTE_KEY).get();
        logger.log(Level.FINE, () -> "exception caught");
        if (exceptionListener != null) {
            exceptionListener.onException(cause);
        }
        final HttpRequestContext httpRequestContext =
                ctx.channel().attr(HttpClientChannelContext.REQUEST_CONTEXT_ATTRIBUTE_KEY).get();
        httpRequestContext.fail(cause.getMessage());
        final ChannelPool channelPool =
                ctx.channel().attr(HttpClientChannelContext.CHANNEL_POOL_ATTRIBUTE_KEY).get();
        channelPool.release(ctx.channel());
    }

    void put(int streamId, ChannelFuture channelFuture, ChannelPromise promise) {
        logger.log(Level.FINE, "put stream ID " + streamId);
        streamidPromiseMap.put(streamId, new AbstractMap.SimpleEntry<>(channelFuture, promise));
    }

    void awaitResponses(HttpRequestContext httpRequestContext, ExceptionListener exceptionListener) {
        int timeout = httpRequestContext.getTimeout();
        Iterator<Entry<Integer, Entry<ChannelFuture, ChannelPromise>>> iterator = streamidPromiseMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<Integer, Entry<ChannelFuture, ChannelPromise>> entry = iterator.next();
            ChannelFuture channelFuture = entry.getValue().getKey();
            if (!channelFuture.awaitUninterruptibly(timeout, TimeUnit.MILLISECONDS)) {
                IllegalStateException illegalStateException =
                        new IllegalStateException("time out while waiting to write for stream id " + entry.getKey());
                if (exceptionListener != null) {
                    exceptionListener.onException(illegalStateException);
                    httpRequestContext.fail(illegalStateException.getMessage());
                    final ChannelPool channelPool =
                            channelFuture.channel().attr(HttpClientChannelContext.CHANNEL_POOL_ATTRIBUTE_KEY).get();
                    channelPool.release(channelFuture.channel());
                }
                throw illegalStateException;
            }
            if (!channelFuture.isSuccess()) {
                throw new RuntimeException(channelFuture.cause());
            }
            ChannelPromise promise = entry.getValue().getValue();
            if (!promise.awaitUninterruptibly(timeout, TimeUnit.MILLISECONDS)) {
                IllegalStateException illegalStateException =
                        new IllegalStateException("time out while waiting for response on stream id " + entry.getKey());
                if (exceptionListener != null) {
                    exceptionListener.onException(illegalStateException);
                    httpRequestContext.fail(illegalStateException.getMessage());
                    final ChannelPool channelPool =
                            channelFuture.channel().attr(HttpClientChannelContext.CHANNEL_POOL_ATTRIBUTE_KEY).get();
                    channelPool.release(channelFuture.channel());
                }
                throw illegalStateException;
            }
            if (!promise.isSuccess()) {
                RuntimeException runtimeException = new RuntimeException(promise.cause());
                if (exceptionListener != null) {
                    exceptionListener.onException(runtimeException);
                    httpRequestContext.fail(runtimeException.getMessage());
                    final ChannelPool channelPool =
                            channelFuture.channel().attr(HttpClientChannelContext.CHANNEL_POOL_ATTRIBUTE_KEY).get();
                    channelPool.release(channelFuture.channel());
                }
                throw runtimeException;
            }
            iterator.remove();
        }
    }
}
