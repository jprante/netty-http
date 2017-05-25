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

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.xbib.netty.http.client.handler.HttpClientChannelInitializer.configureHttp1Pipeline;
import static org.xbib.netty.http.client.handler.HttpClientChannelInitializer.configureHttp2Pipeline;
import static org.xbib.netty.http.client.handler.HttpClientChannelInitializer.createHttp1ConnectionHandler;
import static org.xbib.netty.http.client.handler.HttpClientChannelInitializer.createHttp2ConnectionHandler;

/**
 *
 */
class Http2NegotiationHandler extends ApplicationProtocolNegotiationHandler {

    private static final Logger logger = Logger.getLogger(Http2NegotiationHandler.class.getName());

    private final HttpClientChannelInitializer initializer;

    Http2NegotiationHandler(String fallbackProtocol, HttpClientChannelInitializer initializer) {
        super(fallbackProtocol);
        this.initializer = initializer;
    }

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
            HttpToHttp2ConnectionHandler http2connectionHandler = createHttp2ConnectionHandler(initializer.getContext());
            ctx.pipeline().addLast(http2connectionHandler);
            configureHttp2Pipeline(ctx.pipeline(), initializer.getHttp2ResponseHandler());
            logger.log(Level.FINE, () -> "negotiated HTTP/2: handler = " + ctx.pipeline().names());
            return;
        }
        if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
            HttpClientCodec http1connectionHandler = createHttp1ConnectionHandler(initializer.getContext());
            ctx.pipeline().addLast(http1connectionHandler);
            configureHttp1Pipeline(ctx.pipeline(), initializer.getContext(), initializer.getHttpHandler());
            logger.log(Level.FINE, () -> "negotiated HTTP/1.1: handler = " + ctx.pipeline().names());
            return;
        }
        ctx.close();
        throw new IllegalStateException("unexpected protocol: " + protocol);
    }
}
