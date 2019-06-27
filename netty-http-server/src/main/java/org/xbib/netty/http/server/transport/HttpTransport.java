package org.xbib.netty.http.server.transport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.SslHandler;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.endpoint.NamedServer;

import java.io.IOException;

public class HttpTransport extends BaseTransport {

    public HttpTransport(Server server) {
        super(server);
    }

    @Override
    public void requestReceived(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws IOException {
        requestReceived(ctx, fullHttpRequest, 0);
    }

    @Override
    public void requestReceived(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, Integer sequenceId)
            throws IOException {
        int requestId = requestCounter.incrementAndGet();
        NamedServer namedServer = server.getNamedServer(fullHttpRequest.headers().get(HttpHeaderNames.HOST));
        if (namedServer == null) {
            namedServer = server.getDefaultNamedServer();
        }
        HttpServerRequest serverRequest = new HttpServerRequest();
        serverRequest.setChannelHandlerContext(ctx);
        serverRequest.setRequest(fullHttpRequest);
        serverRequest.setSequenceId(sequenceId);
        serverRequest.setRequestId(requestId);
        SslHandler sslHandler = ctx.channel().pipeline().get(SslHandler.class);
        if (sslHandler != null) {
            serverRequest.setSession(sslHandler.engine().getSession());
        }
        HttpServerResponse serverResponse = new HttpServerResponse(serverRequest);
        if (acceptRequest(namedServer, serverRequest, serverResponse)) {
            handle(namedServer, serverRequest, serverResponse);
        } else {
            ServerResponse.write(serverResponse, HttpResponseStatus.NOT_ACCEPTABLE);
        }
    }

    @Override
    public void settingsReceived(ChannelHandlerContext ctx, Http2Settings http2Settings) {
        // there are no settings in HTTP 1
    }
}
