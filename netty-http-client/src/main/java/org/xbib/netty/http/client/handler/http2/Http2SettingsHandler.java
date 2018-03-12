package org.xbib.netty.http.client.handler.http2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.Http2Settings;
import org.xbib.netty.http.client.transport.Transport;

@ChannelHandler.Sharable
public class Http2SettingsHandler extends SimpleChannelInboundHandler<Http2Settings> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Http2Settings http2Settings) {
        Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
        transport.settingsReceived(ctx.channel(), http2Settings);
        ctx.pipeline().remove(this);
    }
}
