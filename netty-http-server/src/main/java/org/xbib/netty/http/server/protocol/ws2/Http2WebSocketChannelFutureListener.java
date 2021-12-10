package org.xbib.netty.http.server.protocol.ws2;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.GenericFutureListener;
import org.xbib.netty.http.common.ws.Http2WebSocketEvent;

/**
 * ChannelFuture listener that gracefully closes websocket by sending empty DATA frame with
 * END_STREAM flag set.
 */
public final class Http2WebSocketChannelFutureListener implements GenericFutureListener<ChannelFuture> {

    public static final Http2WebSocketChannelFutureListener CLOSE = new Http2WebSocketChannelFutureListener();

    private Http2WebSocketChannelFutureListener() {}

    @Override
    public void operationComplete(ChannelFuture future) {
        Channel channel = future.channel();
        Throwable cause = future.cause();
        if (cause != null) {
            Http2WebSocketEvent.fireFrameWriteError(channel, cause);
        }
        channel.pipeline().fireUserEventTriggered(Http2WebSocketEvent.Http2WebSocketLocalCloseEvent.INSTANCE);
    }
}
