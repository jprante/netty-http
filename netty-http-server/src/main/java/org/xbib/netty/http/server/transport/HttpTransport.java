package org.xbib.netty.http.server.transport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.SslHandler;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.Domain;

import java.io.IOException;

public class HttpTransport extends BaseTransport {

    public HttpTransport(Server server) {
        super(server);
    }

    @Override
    public void requestReceived(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, Integer sequenceId)
            throws IOException {
        Domain domain = server.getNamedServer(fullHttpRequest.headers().get(HttpHeaderNames.HOST));
        HttpServerRequest serverRequest = new HttpServerRequest(server, fullHttpRequest, ctx);
        try {
            serverRequest.setSequenceId(sequenceId);
            serverRequest.setRequestId(server.getRequestCounter().incrementAndGet());
            SslHandler sslHandler = ctx.channel().pipeline().get(SslHandler.class);
            if (sslHandler != null) {
                serverRequest.setSession(sslHandler.engine().getSession());
            }
            HttpServerResponse serverResponse = new HttpServerResponse(server, serverRequest, ctx);
            if (acceptRequest(domain, serverRequest, serverResponse)) {
                serverRequest.handleParameters();
                domain.handle(serverRequest, serverResponse);
            } else {
                ServerResponse.write(serverResponse, HttpResponseStatus.NOT_ACCEPTABLE);
            }
        } finally {
            serverRequest.release();
        }
    }

    @Override
    public void settingsReceived(ChannelHandlerContext ctx, Http2Settings http2Settings) {
        // there are no settings in HTTP 1
    }
}
