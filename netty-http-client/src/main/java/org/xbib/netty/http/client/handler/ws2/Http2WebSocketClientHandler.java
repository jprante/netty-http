package org.xbib.netty.http.client.handler.ws2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateClientExtensionHandshaker;
import io.netty.handler.codec.http2.*;
import io.netty.handler.ssl.SslHandler;
import org.xbib.netty.http.common.ws.Http2WebSocket;
import org.xbib.netty.http.common.ws.Http2WebSocketChannelHandler;
import org.xbib.netty.http.common.ws.Http2WebSocketProtocol;
import org.xbib.netty.http.common.ws.Http2WebSocketValidator;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Provides client-side support for websocket-over-http2. Creates sub channel for http2 stream of
 * successfully handshaked websocket. Subchannel is compatible with http1 websocket handlers. Should
 * be used in tandem with {@link Http2WebSocketClientHandshaker}
 */
public final class Http2WebSocketClientHandler extends Http2WebSocketChannelHandler {

    private static final AtomicReferenceFieldUpdater<Http2WebSocketClientHandler, Http2WebSocketClientHandshaker> HANDSHAKER =
            AtomicReferenceFieldUpdater.newUpdater(Http2WebSocketClientHandler.class, Http2WebSocketClientHandshaker.class, "handshaker");

    private final long handshakeTimeoutMillis;

    private final PerMessageDeflateClientExtensionHandshaker compressionHandshaker;

    private final short streamWeight;

    private CharSequence scheme;

    private Boolean supportsWebSocket;

    private boolean supportsWebSocketCalled;

    private volatile Http2Connection.Endpoint<Http2LocalFlowController> streamIdFactory;

    private volatile Http2WebSocketClientHandshaker handshaker;

    Http2WebSocketClientHandler(
            WebSocketDecoderConfig webSocketDecoderConfig,
            boolean isEncoderMaskPayload,
            short streamWeight,
            long handshakeTimeoutMillis,
            long closedWebSocketRemoveTimeoutMillis,
            PerMessageDeflateClientExtensionHandshaker compressionHandshaker,
            boolean isSingleWebSocketPerConnection) {
        super(
                webSocketDecoderConfig,
                isEncoderMaskPayload,
                closedWebSocketRemoveTimeoutMillis,
                isSingleWebSocketPerConnection);
        this.streamWeight = streamWeight;
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
        this.compressionHandshaker = compressionHandshaker;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        this.scheme =
                ctx.pipeline().get(SslHandler.class) != null
                        ? Http2WebSocketProtocol.SCHEME_HTTPS
                        : Http2WebSocketProtocol.SCHEME_HTTP;
        this.streamIdFactory = http2Handler.connection().local();
    }

    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings)
            throws Http2Exception {
        if (supportsWebSocket != null) {
            super.onSettingsRead(ctx, settings);
            return;
        }
        Long extendedConnectEnabled =
                settings.get(Http2WebSocketProtocol.SETTINGS_ENABLE_CONNECT_PROTOCOL);
        boolean supports =
                supportsWebSocket = extendedConnectEnabled != null && extendedConnectEnabled == 1;
        Http2WebSocketClientHandshaker listener = HANDSHAKER.get(this);
        if (listener != null) {
            supportsWebSocketCalled = true;
            listener.onSupportsWebSocket(supports);
        }
        super.onSettingsRead(ctx, settings);
    }

    @Override
    public void onHeadersRead(
            ChannelHandlerContext ctx,
            int streamId,
            Http2Headers headers,
            int padding,
            boolean endOfStream)
            throws Http2Exception {
        boolean proceed = handshakeWebSocket(streamId, headers, endOfStream);
        if (proceed) {
            next().onHeadersRead(ctx, streamId, headers, padding, endOfStream);
        }
    }

    @Override
    public void onHeadersRead(
            ChannelHandlerContext ctx,
            int streamId,
            Http2Headers headers,
            int streamDependency,
            short weight,
            boolean exclusive,
            int padding,
            boolean endOfStream)
            throws Http2Exception {
        boolean proceed = handshakeWebSocket(streamId, headers, endOfStream);
        if (proceed) {
            next().onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endOfStream);
        }
    }

    Http2WebSocketClientHandshaker handShaker() {
        Http2WebSocketClientHandshaker h = HANDSHAKER.get(this);
        if (h != null) {
            return h;
        }
        Http2Connection.Endpoint<Http2LocalFlowController> streamIdFactory = this.streamIdFactory;
        if (streamIdFactory == null) {
            throw new IllegalStateException(
                    "webSocket handshaker cant be created before channel is registered");
        }
        Http2WebSocketClientHandshaker handShaker =
                new Http2WebSocketClientHandshaker(
                        webSocketsParent,
                        streamIdFactory,
                        config,
                        isEncoderMaskPayload,
                        streamWeight,
                        scheme,
                        handshakeTimeoutMillis,
                        compressionHandshaker);

        if (HANDSHAKER.compareAndSet(this, null, handShaker)) {
            EventLoop el = ctx.channel().eventLoop();
            if (el.inEventLoop()) {
                onSupportsWebSocket(handShaker);
            } else {
                el.execute(() -> onSupportsWebSocket(handShaker));
            }
            return handShaker;
        }
        return HANDSHAKER.get(this);
    }

    private boolean handshakeWebSocket(int streamId, Http2Headers responseHeaders, boolean endOfStream) {
        Http2WebSocket webSocket = webSocketRegistry.get(streamId);
        if (webSocket != null) {
            if (!Http2WebSocketValidator.isValid(responseHeaders)) {
                handShaker().reject(streamId, webSocket, responseHeaders, endOfStream);
            } else {
                handShaker().handshake(webSocket, responseHeaders, endOfStream);
            }
            return false;
        }
        return true;
    }

    private void onSupportsWebSocket(Http2WebSocketClientHandshaker handshaker) {
        if (supportsWebSocketCalled) {
            return;
        }
        Boolean supports = supportsWebSocket;
        if (supports != null) {
            handshaker.onSupportsWebSocket(supports);
        }
    }
}
