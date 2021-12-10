package org.xbib.netty.http.server.protocol.ws2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.concurrent.Future;
import org.xbib.netty.http.common.ws.Http2WebSocketProtocol;
import java.util.List;
import java.util.Objects;

/**
 * Accepts valid websocket-over-http2 request, optionally modifies request and response headers.
 */
public interface Http2WebSocketAcceptor {

    /**
     * @param ctx ChannelHandlerContext of connection channel. Intended for creating acceptor result
     *     with context.executor().newFailedFuture(Throwable),
     *     context.executor().newSucceededFuture(ChannelHandler)
     * @param path websocket path
     * @param subprotocols requested websocket subprotocols. Accepted subprotocol must be set on
     *     response headers, e.g. with {@link Subprotocol#accept(String, Http2Headers)}
     * @param request request headers
     * @param response response headers
     * @return Succeeded future for accepted request, failed for rejected request. It is an error to
     *     return non completed future
     */
    Future<ChannelHandler> accept(ChannelHandlerContext ctx, String path, List<String> subprotocols,
                                  Http2Headers request, Http2Headers response);

    final class Subprotocol {
        private Subprotocol() {}

        public static void accept(String subprotocol, Http2Headers response) {
            Objects.requireNonNull(subprotocol, "subprotocol");
            Objects.requireNonNull(response, "response");
            if (subprotocol.isEmpty()) {
                return;
            }
            response.set(Http2WebSocketProtocol.HEADER_WEBSOCKET_SUBPROTOCOL_NAME, subprotocol);
        }
    }
}
