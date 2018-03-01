package org.xbib.netty.http.client.handler.http1;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContentCompressor;

/**
 * Be sure you place the HttpChunkContentCompressor before the ChunkedWriteHandler.
 */
public class HttpChunkContentCompressor extends HttpContentCompressor {

    HttpChunkContentCompressor(int compressionLevel) {
        super(compressionLevel);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf byteBuf = (ByteBuf) msg;
            if (byteBuf.isReadable()) {
                msg = new DefaultHttpContent(byteBuf);
            }
        }
        super.write(ctx, msg, promise);
    }
}
