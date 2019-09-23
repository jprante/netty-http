/*
 * Copyright 2016 Netflix, Inc.
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
package io.reactivex.netty.protocol.http.ws.client;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker13;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.util.CharsetUtil;
import io.reactivex.netty.protocol.http.ws.internal.WsUtils;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderNames.UPGRADE;
import static io.netty.handler.codec.http.HttpHeaderValues.*;
import static io.reactivex.netty.protocol.http.HttpHandlerNames.*;

/**
 * A channel handler to appropriately setup WebSocket upgrade requests and verify upgrade responses.
 * It also updates the pipeline post a successful upgrade.
 *
 * The handshake code here is taken from {@link WebSocketClientHandshaker13} and not used directly because the APIs
 * do not suit our needs.
 */
public class Ws7To13UpgradeHandler extends ChannelDuplexHandler {

    private String expectedChallengeResponseString;
    private boolean upgraded;

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof HttpRequest) {
            final HttpRequest request = (HttpRequest) msg;
            if (request.headers().contains(UPGRADE, WEBSOCKET, false)) {
                /*
                 * We can safely modify the request here as this request is exclusively for WS upgrades and the following
                 * headers are added for ALL upgrade requests. Since, the handler is single-threaded, these updates do not
                 * step on each other.
                 */
                // Get 16 bit nonce and base 64 encode it
                byte[] nonce = WsUtils.randomBytes(16);
                String key = WsUtils.base64(nonce);
                request.headers().set(SEC_WEBSOCKET_KEY, key);
                String acceptSeed = key + WebSocketClientHandshaker13.MAGIC_GUID;
                byte[] sha1 = WsUtils.sha1(acceptSeed.getBytes(CharsetUtil.US_ASCII));
                expectedChallengeResponseString = WsUtils.base64(sha1);
                String hostHeader = request.headers().get(HOST);
                if (null != hostHeader) {
                    request.headers().set(SEC_WEBSOCKET_ORIGIN, "http://" + hostHeader);
                }
                final ChannelHandlerContext clientCodecCtx = ctx.pipeline().context(HttpClientCodec.getName());
                if (null == clientCodecCtx) {
                    promise.tryFailure(new IllegalStateException(
                            "Http client codec not found, can not upgrade to WebSockets."));
                    return;
                }

                final HttpClientCodec codec =  (HttpClientCodec) clientCodecCtx.handler();

                promise.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            ChannelPipeline p = future.channel().pipeline();
                            // Remove the encoder part of the codec as the user may start writing frames after this method returns.
                            p.addAfter(clientCodecCtx.name(), WsClientEncoder.getName(),
                                       new WebSocket13FrameEncoder(true/*Clients must set this to true*/));
                        }
                    }
                });
            }
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (isUpgradeResponse(msg)) {
            final HttpResponse response = (HttpResponse) msg;
            /*Other verifications are done by WebSocketResponse itself.*/
            String accept = response.headers().get(SEC_WEBSOCKET_ACCEPT);
            if (accept == null || !accept.equals(expectedChallengeResponseString)) {
                throw new WebSocketHandshakeException(String.format(
                        "Invalid challenge. Actual: %s. Expected: %s", accept, expectedChallengeResponseString));
            }

            final ChannelPipeline pipeline = ctx.pipeline();
            ChannelHandlerContext codecCtx = pipeline.context(HttpClientCodec.getName());

            if (null == codecCtx) {
                throw new IllegalStateException("Http codec not found, can not upgrade to WebSocket.");
            }

            pipeline.addAfter(codecCtx.name(), WsClientDecoder.getName(),
                             new WebSocket13FrameDecoder(false/*Clients must set this to false*/, false,
                                                         65555));//TODO: Fix me
            pipeline.remove(HttpClientCodec.class);
            upgraded = true;
        }

        if (upgraded && msg instanceof HttpContent) {
            /*Ignore Content once upgraded. The content should not come typically since an Upgrade accept response is
            empty. The only HttpContent that would come is an empty LastHttpContent that netty generates.*/
            ((HttpContent)msg).release();
            return;
        }

        super.channelRead(ctx, msg);
    }

    private static boolean isUpgradeResponse(Object msg) {
        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;
            HttpHeaders headers = response.headers();
            return response.status().equals(HttpResponseStatus.SWITCHING_PROTOCOLS)
                   && headers.contains(CONNECTION, HttpHeaderValues.UPGRADE, true)
                   && headers.contains(UPGRADE, WEBSOCKET, true)
                   && headers.contains(SEC_WEBSOCKET_ACCEPT);
        }
        return false;
    }
}
