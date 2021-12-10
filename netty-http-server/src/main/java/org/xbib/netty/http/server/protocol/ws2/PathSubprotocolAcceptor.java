package org.xbib.netty.http.server.protocol.ws2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.concurrent.Future;

import java.util.List;

public class PathSubprotocolAcceptor implements Http2WebSocketAcceptor {

    private final ChannelHandler webSocketHandler;

    private final String path;

    private final String subprotocol;

    private final boolean acceptSubprotocol;

    public PathSubprotocolAcceptor(String path, String subprotocol, ChannelHandler webSocketHandler) {
      this(path, subprotocol, webSocketHandler, true);
    }

    public PathSubprotocolAcceptor(String path, String subprotocol, ChannelHandler webSocketHandler, boolean acceptSubprotocol) {
      this.path = path;
      this.subprotocol = subprotocol;
      this.webSocketHandler = webSocketHandler;
      this.acceptSubprotocol = acceptSubprotocol;
    }

    @Override
    public Future<ChannelHandler> accept(ChannelHandlerContext ctx,
                                         String path, List<String> subprotocols, Http2Headers request, Http2Headers response) {
      String subprotocol = this.subprotocol;
      if (path.equals(this.path) && subprotocols.contains(subprotocol)) {
        if (acceptSubprotocol) {
          Subprotocol.accept(subprotocol, response);
        }
        return ctx.executor().newSucceededFuture(webSocketHandler);
      }
      return ctx.executor().newFailedFuture(new Http2WebSocketPathNotFoundException(
                  String.format("Path not found: %s , subprotocols: %s", path, subprotocols)));
    }
}
