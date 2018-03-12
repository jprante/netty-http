package org.xbib.netty.http.server.handler.http2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Netty handler that logs user events.
 */
@ChannelHandler.Sharable
public class UserEventLogger extends ChannelInboundHandlerAdapter {

    private static final Logger logger = Logger.getLogger(UserEventLogger.class.getName());

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        logger.log(Level.FINE, () -> "got user event " + evt);
        ctx.fireUserEventTriggered(evt);
    }
}
