package org.xbib.netty.http.server.transport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.api.ServerResponse;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Http2Transport extends BaseTransport {

    private static final Logger logger = Logger.getLogger(Http2Transport.class.getName());

    public Http2Transport(Server server) {
        super(server);
    }

    @Override
    public void requestReceived(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, Integer sequenceId) throws IOException {
        HttpServerRequest serverRequest = new HttpServerRequest(server, fullHttpRequest, ctx);
        serverRequest.setSequenceId(sequenceId);
        serverRequest.setRequestId(server.getRequestCounter().incrementAndGet());
        serverRequest.setStreamId(fullHttpRequest.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text()));
        ServerResponse serverResponse = new Http2ServerResponse(server, serverRequest, ctx);
        if (acceptRequest(server.getServerConfig().getAddress().getVersion(), serverRequest, serverResponse)) {
            serverRequest.handleParameters();
            server.handle(serverRequest, serverResponse);
        } else {
            ServerResponse.write(serverResponse, HttpResponseStatus.NOT_ACCEPTABLE);
        }
    }

    @Override
    public void settingsReceived(ChannelHandlerContext ctx, Http2Settings http2Settings) {
        logger.log(Level.FINER, "settings received, ignoring");
    }
}
