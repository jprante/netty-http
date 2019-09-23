package org.xbib.netty.http.server.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Idle timeout handler.
 */
@ChannelHandler.Sharable
public class IdleTimeoutHandler extends IdleStateHandler {

    private final Logger logger = Logger.getLogger(IdleTimeoutHandler.class.getName());

    public IdleTimeoutHandler(int idleTimeoutMillis) {
        super(idleTimeoutMillis, idleTimeoutMillis, idleTimeoutMillis);
    }

    @Override
    protected final void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) {
        if (!evt.isFirst()) {
            return;
        }
        logger.log(Level.WARNING, () -> MessageFormat.format("{0} closing an idle connection", ctx.channel()));
        ctx.close();
    }
}
