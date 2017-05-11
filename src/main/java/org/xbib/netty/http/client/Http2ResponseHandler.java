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
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.xbib.netty.http.client.listener.CookieListener;
import org.xbib.netty.http.client.listener.ExceptionListener;
import org.xbib.netty.http.client.listener.HttpHeadersListener;
import org.xbib.netty.http.client.listener.HttpPushListener;
import org.xbib.netty.http.client.listener.HttpResponseListener;

import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Netty channel handler for HTTP/2 responses.
 */
@ChannelHandler.Sharable
public class Http2ResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    private static final Logger logger = Logger.getLogger(Http2ResponseHandler.class.getName());

    private final HttpClient httpClient;

    Http2ResponseHandler(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse httpResponse) throws Exception {
        logger.log(Level.FINE, () -> httpResponse.getClass().getName());
        Integer streamId = httpResponse.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
        if (streamId == null) {
            logger.log(Level.WARNING, () -> "stream ID missing in headers");
            return;
        }
        final HttpRequestContext httpRequestContext =
                ctx.channel().attr(HttpClientChannelContext.REQUEST_CONTEXT_ATTRIBUTE_KEY).get();
        HttpHeaders httpHeaders = httpResponse.headers();
        HttpHeadersListener httpHeadersListener =
                ctx.channel().attr(HttpClientChannelContext.HEADER_LISTENER_ATTRIBUTE_KEY).get();
        if (httpHeadersListener != null) {
            logger.log(Level.FINE, () -> "firing onHeaders");
            httpHeadersListener.onHeaders(httpHeaders);
        }
        CookieListener cookieListener =
                ctx.channel().attr(HttpClientChannelContext.COOKIE_LISTENER_ATTRIBUTE_KEY).get();
        for (String cookieString : httpHeaders.getAll(HttpHeaderNames.SET_COOKIE)) {
            Cookie cookie = ClientCookieDecoder.STRICT.decode(cookieString);
            httpRequestContext.addCookie(cookie);
            if (cookieListener != null) {
                logger.log(Level.FINE, () -> "firing onCookie");
                cookieListener.onCookie(cookie);
            }
        }
        Entry<Http2Headers, ChannelPromise> pushEntry = httpRequestContext.getPushMap().get(streamId);
        if (pushEntry != null) {
            final HttpPushListener httpPushListener =
                    ctx.channel().attr(HttpClientChannelContext.PUSH_LISTENER_ATTRIBUTE_KEY).get();
            if (httpPushListener != null) {
                httpPushListener.onPushReceived(pushEntry.getKey(), httpResponse);
            }
            if (!pushEntry.getValue().isSuccess()) {
                pushEntry.getValue().setSuccess();
            }
            httpRequestContext.getPushMap().remove(streamId);
            if (httpRequestContext.isFinished()) {
                httpRequestContext.success("response finished");
            }
            return;
        }
        Entry<ChannelFuture, ChannelPromise> promiseEntry = httpRequestContext.getStreamIdPromiseMap().get(streamId);
        if (promiseEntry != null) {
            final HttpResponseListener httpResponseListener =
                    ctx.channel().attr(HttpClientChannelContext.RESPONSE_LISTENER_ATTRIBUTE_KEY).get();
            if (httpResponseListener != null) {
                httpResponseListener.onResponse(httpResponse);
            }
            if (!promiseEntry.getValue().isSuccess()) {
                promiseEntry.getValue().setSuccess();
            }
            if (httpClient.tryRedirect(ctx.channel(), httpResponse, httpRequestContext)) {
                return;
            }
            httpRequestContext.getStreamIdPromiseMap().remove(streamId);
            if (httpRequestContext.isFinished()) {
                httpRequestContext.success("response finished");
            }
        }
    }

    /**
     * The only method to release a HTTP/2 channel back to the pool is to wait for inactivity.
     * @param ctx the channel handler context
     * @throws Exception if the channel could not be released back to the pool
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.log(Level.FINE, ctx::toString);
        final ChannelPool channelPool =
                ctx.channel().attr(HttpClientChannelContext.CHANNEL_POOL_ATTRIBUTE_KEY).get();
        channelPool.release(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.log(Level.FINE, () -> "exception caught: " + cause);
        ExceptionListener exceptionListener =
                ctx.channel().attr(HttpClientChannelContext.EXCEPTION_LISTENER_ATTRIBUTE_KEY).get();
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
}
