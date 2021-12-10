package org.xbib.netty.http.common.ws;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AsciiString;

/**
 * Base type for client and server websocket-over-http2 handlers
 */
public abstract class Http2WebSocketHandler extends ChannelDuplexHandler implements Http2FrameListener {

    static final AsciiString HEADER_WEBSOCKET_ENDOFSTREAM_NAME = AsciiString.of("x-websocket-endofstream");

    static final AsciiString HEADER_WEBSOCKET_ENDOFSTREAM_VALUE_TRUE = AsciiString.of("true");

    static final AsciiString HEADER_WEBSOCKET_ENDOFSTREAM_VALUE_FALSE = AsciiString.of("false");

    protected Http2ConnectionHandler http2Handler;

    protected Http2FrameListener next;

    public Http2WebSocketHandler() {}

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        Http2ConnectionHandler http2Handler = this.http2Handler = Preconditions.requireHandler(ctx.channel(), Http2ConnectionHandler.class);
        Http2ConnectionDecoder decoder = http2Handler.decoder();
        Http2FrameListener next = decoder.frameListener();
        decoder.frameListener(this);
        this.next = next;
    }

    @Override
    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {
        next().onGoAwayRead(ctx, lastStreamId, errorCode, debugData);
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
        next().onRstStreamRead(ctx, streamId, errorCode);
    }

    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
        return next().onDataRead(ctx, streamId, data.retain(), padding, endOfStream);
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                              boolean endOfStream) throws Http2Exception {
        next().onHeadersRead(ctx, streamId, headers, padding, endOfStream);
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            int streamDependency, short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
        next().onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endOfStream);
    }

    @Override
    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
            boolean exclusive) throws Http2Exception {
        next().onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
    }

    @Override
    public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
        next().onSettingsAckRead(ctx);
    }

    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings)
            throws Http2Exception {
        next().onSettingsRead(ctx, settings);
    }

    @Override
    public void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
        next().onPingRead(ctx, data);
    }

    @Override
    public void onPingAckRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
        next().onPingAckRead(ctx, data);
    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
            Http2Headers headers, int padding) throws Http2Exception {
        next().onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
    }

    @Override
    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
            throws Http2Exception {
        next().onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
    }

    @Override
    public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf payload)
            throws Http2Exception {
        next().onUnknownFrame(ctx, frameType, streamId, flags, payload);
    }

    protected final Http2FrameListener next() {
        return next;
    }

    static AsciiString endOfStreamName() {
        return HEADER_WEBSOCKET_ENDOFSTREAM_NAME;
    }

    static AsciiString endOfStreamValue(boolean endOfStream) {
        return endOfStream
                ? HEADER_WEBSOCKET_ENDOFSTREAM_VALUE_TRUE
                : HEADER_WEBSOCKET_ENDOFSTREAM_VALUE_FALSE;
    }
}
