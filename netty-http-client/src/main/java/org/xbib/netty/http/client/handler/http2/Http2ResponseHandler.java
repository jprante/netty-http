package org.xbib.netty.http.client.handler.http2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.xbib.netty.http.client.api.ClientTransport;

@ChannelHandler.Sharable
public class Http2ResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse httpResponse) throws Exception {
        Integer streamId = httpResponse.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
        ClientTransport transport = ctx.channel().attr(ClientTransport.TRANSPORT_ATTRIBUTE_KEY).get();
        if (transport != null) {
            transport.responseReceived(ctx.channel(), streamId, httpResponse);
        }
        // do not close ctx here
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelInactive();
        ClientTransport transport = ctx.channel().attr(ClientTransport.TRANSPORT_ATTRIBUTE_KEY).get();
        if (transport != null) {
            transport.inactive(ctx.channel());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.fireExceptionCaught(cause);
        ClientTransport transport = ctx.channel().attr(ClientTransport.TRANSPORT_ATTRIBUTE_KEY).get();
        if (transport != null) {
            transport.fail(ctx.channel(), cause);
        }
        // do not close ctx here
    }
}
