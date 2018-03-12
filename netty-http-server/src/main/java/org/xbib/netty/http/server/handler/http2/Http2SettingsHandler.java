package org.xbib.netty.http.server.handler.http2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.Http2Settings;
import org.xbib.netty.http.server.transport.ServerTransport;

@ChannelHandler.Sharable
public class Http2SettingsHandler extends SimpleChannelInboundHandler<Http2Settings> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Http2Settings http2Settings) throws Exception {
        ServerTransport transport = ctx.channel().attr(ServerTransport.TRANSPORT_ATTRIBUTE_KEY).get();
        transport.settingsReceived(ctx, http2Settings);
        ctx.pipeline().remove(this);
    }
}
