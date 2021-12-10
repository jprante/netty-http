package org.xbib.netty.http.server.protocol.ws2;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;
import io.netty.util.concurrent.GenericFutureListener;
import org.xbib.netty.http.common.ws.Http2WebSocketEvent;
import org.xbib.netty.http.common.ws.Http2WebSocketHandler;
import org.xbib.netty.http.common.ws.Http2WebSocketProtocol;
import org.xbib.netty.http.common.ws.Http2WebSocketValidator;

/**
 * Provides server-side support for websocket-over-http2. Verifies websocket-over-http2 request
 * validity. Invalid websocket requests are rejected by sending RST frame, valid websocket http2
 * stream frames are passed down the pipeline. Valid websocket stream request headers are modified
 * as follows: :method=POST, x-protocol=websocket. Intended for proxies/intermidiaries that do not
 * process websocket byte streams, but only route respective http2 streams - hence is not compatible
 * with http1 websocket handlers. http1 websocket handlers support is provided by complementary
 * {@link Http2WebSocketServerHandler}
 */
public final class Http2WebSocketHandshakeOnlyServerHandler extends Http2WebSocketHandler
        implements GenericFutureListener<ChannelFuture> {

    public Http2WebSocketHandshakeOnlyServerHandler() {}

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                              boolean endOfStream) throws Http2Exception {
        if (handshake(headers, endOfStream)) {
            super.onHeadersRead(ctx, streamId, headers, padding, endOfStream);
        } else {
            reject(ctx, streamId, headers, endOfStream);
        }
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
            short weight, boolean exclusive, int padding, boolean endOfStream)
            throws Http2Exception {
        if (handshake(headers, endOfStream)) {
            super.onHeadersRead(
                    ctx, streamId, headers, streamDependency, weight, exclusive, padding, endOfStream);
        } else {
            reject(ctx, streamId, headers, endOfStream);
        }
    }

    /*RST_STREAM frame write*/
    @Override
    public void operationComplete(ChannelFuture future) {
        Throwable cause = future.cause();
        if (cause != null) {
            Http2WebSocketEvent.fireFrameWriteError(future.channel(), cause);
        }
    }

    private boolean handshake(Http2Headers headers, boolean endOfStream) {
        if (Http2WebSocketProtocol.isExtendedConnect(headers)) {
            boolean isValid = Http2WebSocketValidator.WebSocket.isValid(headers, endOfStream);
            if (isValid) {
                Http2WebSocketServerHandshaker.handshakeOnlyWebSocket(headers);
            }
            return isValid;
        }
        return Http2WebSocketValidator.Http.isValid(headers, endOfStream);
    }

    private void reject(ChannelHandlerContext ctx, int streamId, Http2Headers headers, boolean endOfStream) {
        Http2WebSocketEvent.fireHandshakeValidationStartAndError(ctx.channel(), streamId,
                headers.set( AsciiString.of("x-websocket-endofstream"), AsciiString.of(endOfStream ? "true" : "false")));
        http2Handler.encoder()
                .writeRstStream(ctx, streamId, Http2Error.PROTOCOL_ERROR.code(), ctx.newPromise())
                .addListener(this);
        ctx.flush();
    }
}
