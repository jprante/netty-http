package org.xbib.netty.http.client.handler.http1;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import org.xbib.netty.http.client.transport.Transport;

@ChannelHandler.Sharable
public class HttpResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse httpResponse) {
        Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
        transport.headersReceived(null, httpResponse.headers());
        transport.responseReceived(null, httpResponse);
        transport.success();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
        transport.fail(cause);
        ctx.channel().close();
    }
}
