package org.xbib.netty.http.server.handler.http2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import org.xbib.netty.http.server.transport.ServerTransport;

import java.io.IOException;

@ChannelHandler.Sharable
public class Http2RequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest httpRequest) throws IOException {
        ServerTransport transport = ctx.channel().attr(ServerTransport.TRANSPORT_ATTRIBUTE_KEY).get();
        transport.requestReceived(ctx, httpRequest);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx)  {
        ctx.fireChannelInactive();
        ServerTransport transport = ctx.channel().attr(ServerTransport.TRANSPORT_ATTRIBUTE_KEY).get();
        //transport.fail(new IOException("channel closed"));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ServerTransport transport = ctx.channel().attr(ServerTransport.TRANSPORT_ATTRIBUTE_KEY).get();
        //transport.fail(cause);
        ctx.channel().close();
    }
}
