package org.xbib.netty.http.client.handler.http2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.xbib.netty.http.client.api.Transport;

@ChannelHandler.Sharable
public class Http2ResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse httpResponse) throws Exception {
        Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
        Integer streamId = httpResponse.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
        transport.responseReceived(ctx.channel(), streamId, httpResponse);
        // do not close ctx here
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
        transport.fail(cause);
        // do not close ctx here
    }
}
