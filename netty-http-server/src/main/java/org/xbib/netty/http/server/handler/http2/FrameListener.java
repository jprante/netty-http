package org.xbib.netty.http.server.handler.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;

import java.util.logging.Level;
import java.util.logging.Logger;

public class FrameListener extends Http2EventAdapter {

    private static final Logger logger = Logger.getLogger(FrameListener.class.getName());

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                              short weight, boolean exclusive, int padding, boolean endStream) {
        logger.log(Level.FINE, "onHeadersRead");
        Http2HeadersFrame frame = new DefaultHttp2HeadersFrame(headers,endStream,padding);
        ctx.fireChannelRead(frame);
    }

    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
        logger.log(Level.FINE, "onDataRead");
        Http2DataFrame frame = new DefaultHttp2DataFrame(data, endOfStream, padding);
        ctx.fireChannelRead(frame);
        return data.readableBytes() + padding;
    }
}
