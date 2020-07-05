package org.xbib.netty.http.client.handler.http;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import org.xbib.netty.http.client.api.ClientTransport;

@ChannelHandler.Sharable
public class HttpResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpResponse fullHttpResponse) throws Exception {
        ClientTransport transport = ctx.channel().attr(ClientTransport.TRANSPORT_ATTRIBUTE_KEY).get();
        if (transport != null) {
            transport.responseReceived(ctx.channel(), null, fullHttpResponse);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.fireExceptionCaught(cause);
        ClientTransport transport = ctx.channel().attr(ClientTransport.TRANSPORT_ATTRIBUTE_KEY).get();
        if (transport != null) {
            transport.fail(ctx.channel(), cause);
        }
    }
}
