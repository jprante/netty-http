package org.xbib.netty.http.client.handler.http2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.xbib.netty.http.client.transport.Transport;
import org.xbib.netty.http.common.DefaultHttpResponse;

@ChannelHandler.Sharable
public class Http2ResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse httpResponse) throws Exception {
        Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
        Integer streamId = httpResponse.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
        transport.responseReceived(ctx.channel(), streamId,
                new DefaultHttpResponse(transport.getHttpAddress(), httpResponse.retain()));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
        transport.fail(cause);
        ctx.channel().close();
    }
}
