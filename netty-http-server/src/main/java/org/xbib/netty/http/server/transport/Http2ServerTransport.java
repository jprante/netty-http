package org.xbib.netty.http.server.transport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.endpoint.NamedServer;

import java.io.IOException;

public class Http2ServerTransport extends BaseServerTransport {

    public Http2ServerTransport(Server server) {
        super(server);
    }

    @Override
    public void requestReceived(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws IOException {
        requestReceived(ctx, fullHttpRequest, null);
    }

    @Override
    public void requestReceived(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, Integer sequenceId) throws IOException {
        int requestId = requestCounter.incrementAndGet();
        NamedServer namedServer = server.getNamedServer(fullHttpRequest.headers().get(HttpHeaderNames.HOST));
        if (namedServer == null) {
            namedServer = server.getDefaultNamedServer();
        }
        Integer streamId = fullHttpRequest.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
        HttpServerRequest serverRequest = new HttpServerRequest();
        serverRequest.setNamedServer(namedServer);
        serverRequest.setChannelHandlerContext(ctx);
        serverRequest.setRequest(fullHttpRequest);
        serverRequest.setSequenceId(sequenceId);
        serverRequest.setRequestId(requestId);
        serverRequest.setStreamId(streamId);
        ServerResponse serverResponse = new Http2ServerResponse(serverRequest);
        if (acceptRequest(serverRequest, serverResponse)) {
            handle(serverRequest, serverResponse);
        } else {
           serverResponse.write(HttpResponseStatus.NOT_ACCEPTABLE);
        }
    }

    @Override
    public void settingsReceived(ChannelHandlerContext ctx, Http2Settings http2Settings) {
    }
}
