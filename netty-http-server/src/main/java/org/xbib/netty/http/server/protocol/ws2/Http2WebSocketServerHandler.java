package org.xbib.netty.http.server.protocol.ws2;

import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import org.xbib.netty.http.common.ws.Http2WebSocketChannelHandler;
import org.xbib.netty.http.common.ws.Http2WebSocketProtocol;
import org.xbib.netty.http.common.ws.Http2WebSocketValidator;

/**
 * Provides server-side support for websocket-over-http2. Creates sub channel for http2 stream of
 * successfully handshaked websocket. Subchannel is compatible with http1 websocket handlers.
 */
public final class Http2WebSocketServerHandler extends Http2WebSocketChannelHandler {

    private final PerMessageDeflateServerExtensionHandshaker compressionHandshaker;

    private final Http2WebSocketAcceptor http2WebSocketAcceptor;

    private Http2WebSocketServerHandshaker handshaker;

    Http2WebSocketServerHandler(WebSocketDecoderConfig webSocketDecoderConfig, boolean isEncoderMaskPayload,
            long closedWebSocketRemoveTimeoutMillis,
            PerMessageDeflateServerExtensionHandshaker compressionHandshaker,
            Http2WebSocketAcceptor http2WebSocketAcceptor,
            boolean isSingleWebSocketPerConnection) {
        super(webSocketDecoderConfig, isEncoderMaskPayload, closedWebSocketRemoveTimeoutMillis, isSingleWebSocketPerConnection);
        this.compressionHandshaker = compressionHandshaker;
        this.http2WebSocketAcceptor = http2WebSocketAcceptor;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        this.handshaker = new Http2WebSocketServerHandshaker(webSocketsParent,
                config, isEncoderMaskPayload, http2WebSocketAcceptor, compressionHandshaker);
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, final int streamId, Http2Headers headers,
            int padding, boolean endOfStream) throws Http2Exception {
        boolean proceed = handshakeWebSocket(streamId, headers, endOfStream);
        if (proceed) {
            next().onHeadersRead(ctx, streamId, headers, padding, endOfStream);
        }
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
            short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
        boolean proceed = handshakeWebSocket(streamId, headers, endOfStream);
        if (proceed) {
            next().onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endOfStream);
        }
    }

    private boolean handshakeWebSocket(int streamId, Http2Headers headers, boolean endOfStream) {
        if (Http2WebSocketProtocol.isExtendedConnect(headers)) {
            if (!Http2WebSocketValidator.WebSocket.isValid(headers, endOfStream)) {
                handshaker.reject(streamId, headers, endOfStream);
            } else {
                handshaker.handshake(streamId, headers, endOfStream);
            }
            return false;
        }
        if (!Http2WebSocketValidator.Http.isValid(headers, endOfStream)) {
            handshaker.reject(streamId, headers, endOfStream);
            return false;
        }
        return true;
    }
}
