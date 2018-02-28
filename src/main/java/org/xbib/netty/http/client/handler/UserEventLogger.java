package org.xbib.netty.http.client.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.handler.ssl.SslCloseCompletionEvent;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Netty handler that logs user events and find expetced ones.
 */
@ChannelHandler.Sharable
class UserEventLogger extends ChannelInboundHandlerAdapter {

    private static final Logger logger = Logger.getLogger(UserEventLogger.class.getName());

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        logger.log(Level.FINE, () -> "got user event " + evt);
        if (evt instanceof SslCloseCompletionEvent ||
                evt instanceof ChannelInputShutdownReadComplete) {
            logger.log(Level.FINE, () -> "user event is expected: " + evt);
            return;
        }
        super.userEventTriggered(ctx, evt);
    }
}
