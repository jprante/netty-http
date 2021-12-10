package org.xbib.netty.http.server.protocol.ws2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.concurrent.Future;

import java.util.List;

public class PathAcceptor implements Http2WebSocketAcceptor {

    private final String path;

    private final ChannelHandler webSocketHandler;

    public PathAcceptor(String path, ChannelHandler webSocketHandler) {
      this.path = path;
      this.webSocketHandler = webSocketHandler;
    }

    @Override
    public Future<ChannelHandler> accept(ChannelHandlerContext ctx, String path, List<String> subprotocols,
                                         Http2Headers request, Http2Headers response) {
      if (subprotocols.isEmpty() && path.equals(this.path)) {
        return ctx.executor().newSucceededFuture(webSocketHandler);
      }
      return ctx.executor().newFailedFuture(new WebSocketHandshakeException(String.format("Path not found: %s , subprotocols: %s", path, subprotocols)));
    }
}
