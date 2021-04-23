package org.xbib.netty.http.server.protocol.http2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.ssl.SslHandler;
import org.xbib.netty.http.server.AcceptState;
import org.xbib.netty.http.server.BaseTransport;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.api.ServerResponse;
import org.xbib.netty.http.server.HttpServerRequest;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Http2Transport extends BaseTransport {

    private static final Logger logger = Logger.getLogger(Http2Transport.class.getName());

    public Http2Transport(Server server) {
        super(server);
    }

    @Override
    public void requestReceived(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, Integer sequenceId) throws IOException {
        AcceptState acceptState = acceptRequest(server.getServerConfig().getAddress().getVersion(),
                fullHttpRequest.headers());
        Integer streamId = fullHttpRequest.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
        ServerResponse.Builder serverResponseBuilder = Http2ServerResponse.builder(ctx)
                .setResponseId(server.getResponseCounter().incrementAndGet())
                .setStreamId(streamId)
                .setSequenceId(sequenceId);
        switch (acceptState) {
            case OK: {
                HttpServerRequest.Builder serverRequestBuilder = HttpServerRequest.builder()
                        .setHttpRequest(fullHttpRequest.retainedDuplicate())
                        .setLocalAddress((InetSocketAddress) ctx.channel().localAddress())
                        .setRemoteAddress((InetSocketAddress) ctx.channel().remoteAddress())
                        .setStreamId(streamId)
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
                serverResponseBuilder
                        .setStatus(HttpResponseStatus.BAD_REQUEST.code())
                        .setContentType("text/plain")
                        .build()
                        .write("missing 'Host' header");
            }
            case EXPECTATION_FAILED: {
                serverResponseBuilder
                        .setStatus(HttpResponseStatus.EXPECTATION_FAILED.code())
                        .build()
                        .flush();
                break;
            }
            case UNSUPPORTED_HTTP_VERSION: {
                serverResponseBuilder
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
        logger.log(Level.FINER, "settings received, ignoring");
    }
}
