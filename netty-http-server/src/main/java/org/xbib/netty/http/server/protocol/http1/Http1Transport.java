package org.xbib.netty.http.server.protocol.http1;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.SslHandler;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.api.ServerResponse;
import org.xbib.netty.http.server.AcceptState;
import org.xbib.netty.http.server.BaseTransport;
import org.xbib.netty.http.server.HttpServerRequest;
import java.io.IOException;
import java.net.InetSocketAddress;

public class Http1Transport extends BaseTransport {

    public Http1Transport(Server server) {
        super(server);
    }

    @Override
    public void requestReceived(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, Integer sequenceId) throws IOException {
        AcceptState acceptState = acceptRequest(server.getServerConfig().getAddress().getVersion(),
                fullHttpRequest.headers());
        ServerResponse.Builder serverResponseBuilder = HttpServerResponse.builder(ctx)
                .setResponseId(server.getResponseCounter().incrementAndGet());
        switch (acceptState) {
            case OK: {
                HttpServerRequest.Builder serverRequestBuilder = HttpServerRequest.builder()
                        .setLocalAddress((InetSocketAddress) ctx.channel().localAddress())
                        .setRemoteAddress((InetSocketAddress) ctx.channel().remoteAddress())
                        .setHttpRequest(fullHttpRequest.retainedDuplicate())
                        .setSequenceId(sequenceId)
                        .setRequestId(server.getRequestCounter().incrementAndGet());
                SslHandler sslHandler = ctx.channel().pipeline().get(SslHandler.class);
                if (sslHandler != null) {
                    serverRequestBuilder.setSession(sslHandler.engine().getSession());
                }
                boolean shouldClose = "close".equalsIgnoreCase(fullHttpRequest.headers().get(HttpHeaderNames.CONNECTION));
                serverResponseBuilder.shouldClose(shouldClose);
                server.handle(serverRequestBuilder, serverResponseBuilder);
                break;
            }
            case MISSING_HOST_HEADER: {
                HttpServerResponse.builder(ctx)
                        .setStatus(HttpResponseStatus.BAD_REQUEST.code())
                        .setContentType("text/plain")
                        .build()
                        .write("missing 'Host' header");
            }
            case EXPECTATION_FAILED: {
                HttpServerResponse.builder(ctx)
                        .setStatus(HttpResponseStatus.EXPECTATION_FAILED.code())
                        .build()
                        .flush();
                break;
            }
            case UNSUPPORTED_HTTP_VERSION: {
                HttpServerResponse.builder(ctx)
                        .setStatus(HttpResponseStatus.BAD_REQUEST.code())
                        .setContentType("text/plain")
                        .build()
                        .write("unsupported HTTP version");
                break;
            }
        }
    }

    @Override
    public void settingsReceived(ChannelHandlerContext ctx, Http2Settings http2Settings) {
        // there are no settings in HTTP 1
    }
}
