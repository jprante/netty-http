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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.pool.ChannelPool;
import io.netty.handler.codec.http.FullHttpResponse;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Netty channel handler for HTTP 1.1.
 */
@ChannelHandler.Sharable
final class Http1Handler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = Logger.getLogger(Http1Handler.class.getName());

    private final HttpClient httpClient;

    Http1Handler(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     *
     * Read channel message, hand over content to response handler, and redirect to next URL if possible.
     * @param ctx the channel handler context
     * @param msg the channel message
     * @throws Exception if processing of channel message fails
     */
    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        logger.log(Level.FINE, () -> "channelRead msg " + msg.getClass().getName());
        final HttpRequestContext httpRequestContext =
                ctx.channel().attr(HttpClientChannelContext.REQUEST_CONTEXT_ATTRIBUTE_KEY).get();
        if (msg instanceof FullHttpResponse) {
            FullHttpResponse httpResponse = (FullHttpResponse) msg;
            HttpResponseListener httpResponseListener =
                    ctx.channel().attr(HttpClientChannelContext.RESPONSE_LISTENER_ATTRIBUTE_KEY).get();
            if (httpResponseListener != null) {
                httpResponseListener.onResponse(httpResponse);
            }
            if (httpClient.tryRedirect(ctx.channel(), httpResponse, httpRequestContext)) {
                return;
            }
            logger.log(Level.FINE, () -> "success");
            httpRequestContext.success("response arrived");
            final ChannelPool channelPool =
                    ctx.channel().attr(HttpClientChannelContext.CHANNEL_POOL_ATTRIBUTE_KEY).get();
            channelPool.release(ctx.channel());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.log(Level.FINE, () -> "channelInactive " + ctx);
        final HttpRequestContext httpRequestContext =
                ctx.channel().attr(HttpClientChannelContext.REQUEST_CONTEXT_ATTRIBUTE_KEY).get();
        if (httpRequestContext.getRedirectCount().get() == 0 && !httpRequestContext.isSucceeded()) {
            httpRequestContext.fail("channel inactive");
        }
        final ChannelPool channelPool =
                ctx.channel().attr(HttpClientChannelContext.CHANNEL_POOL_ATTRIBUTE_KEY).get();
        channelPool.release(ctx.channel());
    }

    /**
     * Forward channel exceptions to the exception listener.
     * @param ctx the channel handler context
     * @param cause the cause of the exception
     * @throws Exception if forwarding fails
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ExceptionListener exceptionListener =
                ctx.channel().attr(HttpClientChannelContext.EXCEPTION_LISTENER_ATTRIBUTE_KEY).get();
        logger.log(Level.FINE, () -> "exceptionCaught");
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
