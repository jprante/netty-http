package org.xbib.netty.http.server.handler.http2;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DummyHandler extends ChannelDuplexHandler {

    private static final Logger logger = Logger.getLogger(DummyHandler.class.getName());

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        logger.log(Level.INFO, "msg = " + msg + " class = " + msg.getClass().getName());
    }
}
