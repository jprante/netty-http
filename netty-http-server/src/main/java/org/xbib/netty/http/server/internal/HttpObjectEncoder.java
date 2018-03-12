package org.xbib.netty.http.server.internal;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2Error;

/**
 * HTTP object encoder.
 */
public abstract class HttpObjectEncoder {

    private volatile boolean closed;

    /**
     * Writes an {@link HttpHeaders}.
     */
    public final ChannelFuture writeHeaders(ChannelHandlerContext ctx, int id, int streamId, HttpHeaders headers,
                                            HttpResponseStatus status, boolean endStream) {
        if (!ctx.channel().eventLoop().inEventLoop()) {
            throw new IllegalStateException();
        }
        if (closed) {
            return newFailedFuture(ctx);
        }
        return doWriteHeaders(ctx, id, streamId, headers, status, endStream);
    }

    protected abstract ChannelFuture doWriteHeaders(ChannelHandlerContext ctx, int id, int streamId,
                                                    HttpHeaders headers,  HttpResponseStatus status, boolean endStream);

    public final ChannelFuture writeData(ChannelHandlerContext ctx, int id, int streamId, ByteBuf data, boolean endStream) {
        if (!ctx.channel().eventLoop().inEventLoop()) {
            throw new IllegalStateException();
        }
        if (closed) {
            return newFailedFuture(ctx);
        }
        return doWriteData(ctx, id, streamId, data, endStream);
    }

    protected abstract ChannelFuture doWriteData(ChannelHandlerContext ctx, int id, int streamId, ByteBuf data,
                                                 boolean endStream);

    /**
     * Resets the specified stream. If the session protocol doesn't support multiplexing or the connection
     * is in unrecoverable state, the connection will be closed. For example, in an HTTP/1 connection, this
     * will lead the connection to be closed immediately or after the previous requests that are not reset.
     */
    public final ChannelFuture writeReset(ChannelHandlerContext ctx, int id, int streamId, Http2Error error) {
        if (closed) {
            return newFailedFuture(ctx);
        }
        return doWriteReset(ctx, id, streamId, error);
    }

    protected abstract ChannelFuture doWriteReset(ChannelHandlerContext ctx, int id, int streamId, Http2Error error);

    /**
     * Releases the resources related with this encoder and fails any unfinished writes.
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        doClose();
    }

    protected abstract void doClose();

    private static ChannelFuture newFailedFuture(ChannelHandlerContext ctx) {
        return ctx.newFailedFuture(new ClosedSessionException());
    }
}
