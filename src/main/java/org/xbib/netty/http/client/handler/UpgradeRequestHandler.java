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
package org.xbib.netty.http.client.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.xbib.netty.http.client.HttpClientChannelContextDefaults;
import org.xbib.netty.http.client.HttpRequestContext;
import org.xbib.netty.http.client.listener.ExceptionListener;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
@ChannelHandler.Sharable
class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = Logger.getLogger(UpgradeRequestHandler.class.getName());

    /**
     * Send an upgrade request if channel becomes active.
     * @param ctx the channel handler context
     * @throws Exception if upgrade request sending fails
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        DefaultFullHttpRequest upgradeRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        ctx.writeAndFlush(upgradeRequest);
        super.channelActive(ctx);
        ctx.pipeline().remove(this);
        logger.log(Level.FINE, () -> "upgrade request handler removed, pipeline = " + ctx.pipeline().names());
    }

    /**
     * Forward channel exceptions to the exception listener.
     * @param ctx the channel handler context
     * @param cause the cause of the exception
     * @throws Exception if forwarding fails
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.log(Level.FINE, () -> "exceptionCaught " + cause.getMessage());
        ExceptionListener exceptionListener =
                ctx.channel().attr(HttpClientChannelContextDefaults.EXCEPTION_LISTENER_ATTRIBUTE_KEY).get();
        if (exceptionListener != null) {
            exceptionListener.onException(cause);
        }
        final HttpRequestContext httpRequestContext =
                ctx.channel().attr(HttpClientChannelContextDefaults.REQUEST_CONTEXT_ATTRIBUTE_KEY).get();
        httpRequestContext.fail(cause.getMessage());
    }
}
