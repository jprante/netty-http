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
package io.reactivex.netty.protocol.http.ws.server;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker07;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker08;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker13;
import io.netty.util.CharsetUtil;
import io.reactivex.netty.channel.Connection;
import io.reactivex.netty.channel.MarkAwarePipeline;
import io.reactivex.netty.protocol.http.internal.AbstractHttpConnectionBridge;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.ws.WebSocketConnection;
import io.reactivex.netty.protocol.http.ws.internal.WsUtils;
import io.reactivex.netty.protocol.http.ws.server.V7to13Handshaker.State;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func0;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.*;
import static io.reactivex.netty.protocol.http.HttpHandlerNames.*;

/**
 * A websocket upgrade handler for upgrading to WebSocket versions 7 to 13. This handler listens for
 * {@link WebSocket7To13UpgradeAcceptedEvent} and upon recieving such an event, it sets up the
 * {@link WebSocketConnection} to hand it over to the associated {@link WebSocketHandler}
 */
public final class Ws7To13UpgradeHandler extends ChannelDuplexHandler {

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

        if (evt instanceof WebSocket7To13UpgradeAcceptedEvent) {

            final WebSocket7To13UpgradeAcceptedEvent wsUpEvt = (WebSocket7To13UpgradeAcceptedEvent) evt;

            final State state = wsUpEvt.state;
            final Subscriber<? super Void> subscriber = wsUpEvt.acceptUpgradeSubscriber;

            String errorIfAny = configureResponseForWs(state);

            if (null != errorIfAny) {
                subscriber.onError(new IllegalStateException(errorIfAny));
                return;
            }

            final MarkAwarePipeline pipeline = state.getUpgradeResponse().unsafeConnection()
                                                    .getResettableChannelPipeline();

            @SuppressWarnings("unchecked")
            final Connection<WebSocketFrame, WebSocketFrame> wsConn =
                    (Connection<WebSocketFrame, WebSocketFrame>) wsUpEvt.state.getUpgradeResponse().unsafeConnection();

            wsUpEvt.request.discardContent()
                           .onErrorResumeNext(Observable.<Void>empty()) // In case, the request content was read, ignore.
                           .concatWith(state.getUpgradeResponse().setTransferEncodingChunked().sendHeaders())
                           .doOnCompleted(new Action0() {
                               @Override
                               public void call() {
                                   /*We are no more talking HTTP*/
                                   pipeline.remove(HttpServerEncoder.getName());
                                   pipeline.remove(HttpServerDecoder.getName());
                                   pipeline.channel().attr(AbstractHttpConnectionBridge.CONNECTION_UPGRADED).set(true);
                               }
                           })
                           .concatWith(Observable.defer(new Func0<Observable<Void>>() {
                               @Override
                               public Observable<Void> call() {
                                   return wsUpEvt.handler.handle(new WebSocketConnection(wsConn));
                               }
                           }))
                           .concatWith(Observable.create(new OnSubscribe<Void>() {
                               @Override
                               public void call(Subscriber<? super Void> sub) {
                                   /*
                                    * In this case, the client did not send a close frame but the server end processing
                                    * is over, so we should send a close frame to indicate closure from server.
                                    */
                                   if (wsConn.unsafeNettyChannel().isOpen()) {
                                       wsConn.write(Observable.<WebSocketFrame>just(new CloseWebSocketFrame()))
                                             .concatWith(wsConn.close())
                                             .unsafeSubscribe(sub);
                                   }
                               }
                           }))
                           .unsafeSubscribe(subscriber); /*Unsafe as the subscriber is coming from the user.*/

        }

        ctx.fireUserEventTriggered(evt);
    }

    private static String configureResponseForWs(State state) {

        String acceptGuid;

        switch (state.getVersion()) {
        case V07:
            acceptGuid = WebSocketServerHandshaker07.WEBSOCKET_07_ACCEPT_GUID;
            break;
        case V08:
            acceptGuid = WebSocketServerHandshaker08.WEBSOCKET_08_ACCEPT_GUID;
            break;
        case V13:
            acceptGuid = WebSocketServerHandshaker13.WEBSOCKET_13_ACCEPT_GUID;
            break;
        default:
            return "Unsupported web socket version: " + state.getVersion();
        }

        WebSocketFrameEncoder wsEncoder = new WebSocket13FrameEncoder(false /*servers should set this to false.*/);
        WebSocketFrameDecoder wsDecoder = new WebSocket13FrameDecoder(true/*servers should set this to true.*/,
                                                                      state.isAllowExtensions(),
                                                                      state.getMaxFramePayloadLength(), true);

        final HttpServerResponse<?> upgradeResponse = state.getUpgradeResponse();
        final MarkAwarePipeline pipeline = upgradeResponse.unsafeConnection().getResettableChannelPipeline();
        ChannelHandlerContext httpDecoderCtx = pipeline.context(HttpServerDecoder.getName());
        if (null == httpDecoderCtx) {
            return "No HTTP decoder found, can not upgrade to WebSocket.";
        }
        ChannelHandlerContext httpEncoderCtx = pipeline.context(HttpServerEncoder.getName());
        if (null == httpEncoderCtx) {
            return "No HTTP encoder found, can not upgrade to WebSocket.";
        }

        pipeline.addAfter(httpDecoderCtx.name(), WsServerDecoder.getName(), wsDecoder);
        pipeline.addBefore(httpEncoderCtx.name(), WsServerEncoder.getName(), wsEncoder);

        updateHandshakeHeaders(state, acceptGuid, upgradeResponse);

        return null;
    }

    private static void updateHandshakeHeaders(State state, String acceptGuid, HttpServerResponse<?> upgradeResponse) {
        String acceptSeed = state.getSecWSkey() + acceptGuid;
        byte[] sha1 = WsUtils.sha1(acceptSeed.getBytes(CharsetUtil.US_ASCII));
        String accept = WsUtils.base64(sha1);

        upgradeResponse.addHeader(SEC_WEBSOCKET_ACCEPT, accept);
        upgradeResponse.setStatus(HttpResponseStatus.SWITCHING_PROTOCOLS);
        upgradeResponse.addHeader(HttpHeaderNames.UPGRADE, WEBSOCKET);
        upgradeResponse.addHeader(CONNECTION, HttpHeaderValues.UPGRADE);

        if (state.getRequestSubProtocols() != null) {
            String selectedSubprotocol = WebSocketHandshaker.selectSubprotocol(state.getRequestSubProtocols(),
                                                                               state.getSupportedSubProtocols());
            if (selectedSubprotocol != null) {
                state.getUpgradeResponse().addHeader(SEC_WEBSOCKET_PROTOCOL, selectedSubprotocol);
            }
        }
    }

    public static class WebSocket7To13UpgradeAcceptedEvent {

        private final Subscriber<? super Void> acceptUpgradeSubscriber;
        private final WebSocketHandler handler;
        private final State state;
        private final HttpServerRequest<?> request;

        WebSocket7To13UpgradeAcceptedEvent(Subscriber<? super Void> acceptUpgradeSubscriber, WebSocketHandler handler,
                                           State state, HttpServerRequest<?> request) {
            this.acceptUpgradeSubscriber = acceptUpgradeSubscriber;
            this.handler = handler;
            this.state = state;
            this.request = request;
        }
    }
}
