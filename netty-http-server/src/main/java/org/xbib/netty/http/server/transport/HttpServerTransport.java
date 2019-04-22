package org.xbib.netty.http.server.transport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2Settings;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.context.VirtualServer;

import java.io.IOException;

public class HttpServerTransport extends BaseServerTransport {

    public HttpServerTransport(Server server) {
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
        VirtualServer virtualServer = server.getVirtualServer(fullHttpRequest.headers().get(HttpHeaderNames.HOST));
        if (virtualServer == null) {
            virtualServer = server.getDefaultVirtualServer();
        }
        HttpAddress httpAddress = server.getServerConfig().getAddress();
        ServerRequest serverRequest = new ServerRequest(virtualServer, httpAddress, fullHttpRequest,
                 sequenceId, null, requestId);
        ServerResponse serverResponse = new HttpServerResponse(serverRequest, ctx);
        if (acceptRequest(serverRequest, serverResponse)) {
            handle(serverRequest, serverResponse);
        } else {
            serverResponse.write(HttpResponseStatus.NOT_ACCEPTABLE);
        }
    }

    @Override
    public void settingsReceived(ChannelHandlerContext ctx, Http2Settings http2Settings) {
        // there are no settings in HTTP 1
    }
}
