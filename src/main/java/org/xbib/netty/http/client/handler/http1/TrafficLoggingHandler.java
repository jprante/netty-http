package org.xbib.netty.http.client.handler.http1;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * A Netty handler that logs the I/O traffic of a connection.
 */
@ChannelHandler.Sharable
public class TrafficLoggingHandler extends LoggingHandler {

    public TrafficLoggingHandler(LogLevel level) {
        super("client", level);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        ctx.fireChannelUnregistered();
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf && !((ByteBuf) msg).isReadable()) {
            ctx.write(msg, promise);
        } else {
            super.write(ctx, msg, promise);
        }
    }
}
