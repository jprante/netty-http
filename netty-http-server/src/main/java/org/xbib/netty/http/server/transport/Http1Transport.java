package org.xbib.netty.http.server.transport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.SslHandler;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.api.ServerResponse;
import java.io.IOException;

public class Http1Transport extends BaseTransport {

    public Http1Transport(Server server) {
        super(server);
    }

    @Override
    public void requestReceived(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, Integer sequenceId) throws IOException {
        HttpServerRequest serverRequest = new HttpServerRequest(server, fullHttpRequest, ctx);
        serverRequest.setSequenceId(sequenceId);
        serverRequest.setRequestId(server.getRequestCounter().incrementAndGet());
        SslHandler sslHandler = ctx.channel().pipeline().get(SslHandler.class);
        if (sslHandler != null) {
            serverRequest.setSession(sslHandler.engine().getSession());
        }
        HttpServerResponse serverResponse = new HttpServerResponse(server, serverRequest, ctx);
        if (acceptRequest(server.getServerConfig().getAddress().getVersion(), serverRequest, serverResponse)) {
            serverRequest.handleParameters();
            server.handle(serverRequest, serverResponse);
        } else {
            ServerResponse.write(serverResponse, HttpResponseStatus.NOT_ACCEPTABLE);
        }
    }

    @Override
    public void settingsReceived(ChannelHandlerContext ctx, Http2Settings http2Settings) {
        // there are no settings in HTTP 1
    }
}
