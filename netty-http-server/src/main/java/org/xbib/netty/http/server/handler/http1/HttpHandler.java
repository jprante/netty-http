package org.xbib.netty.http.server.handler.http1;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.transport.ServerTransport;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP handler.
 */
@ChannelHandler.Sharable
public class HttpHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = Logger.getLogger(HttpHandler.class.getName());

    private final Server server;

    public HttpHandler(Server server) {
        this.server = server;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest fullHttpRequest = (FullHttpRequest) msg;
            ServerTransport serverTransport = server.newTransport(fullHttpRequest.protocolVersion());
            serverTransport.requestReceived(ctx, fullHttpRequest);
        } else {
            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.log(Level.WARNING, cause.getMessage(), cause);
        ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Unpooled.copiedBuffer(cause.getMessage().getBytes(StandardCharsets.UTF_8))));
    }
}
