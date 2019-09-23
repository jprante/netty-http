package org.xbib.netty.http.client.handler.http;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import org.xbib.netty.http.client.api.Transport;

@ChannelHandler.Sharable
public class HttpResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpResponse fullHttpResponse) throws Exception {
        Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
        transport.responseReceived(ctx.channel(), null, fullHttpResponse);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
        transport.fail(cause);
        ctx.channel().close();
    }
}
