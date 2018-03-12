package org.xbib.netty.http.server.transport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.Http2Settings;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.context.VirtualServer;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Http2ServerTransport extends BaseServerTransport {

    private static final Logger logger = Logger.getLogger(Http2ServerTransport.class.getName());

    public Http2ServerTransport(Server server) {
        super(server);
    }

    @Override
    public void requestReceived(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws IOException {
        logger.log(Level.INFO, "requestReceived");
        int requestId = requestCounter.incrementAndGet();
        VirtualServer virtualServer = server.getVirtualServer(fullHttpRequest.headers().get(HttpHeaderNames.HOST));
        if (virtualServer == null) {
            virtualServer = server.getDefaultVirtualServer();
        }
        HttpAddress httpAddress = server.getServerConfig().getAddress();
        ServerRequest serverRequest = new ServerRequest(virtualServer, httpAddress, fullHttpRequest,
                null, requestId);
        ServerResponse serverResponse = new Http1ServerResponse(httpAddress.getVersion(), serverRequest, ctx);
        if (acceptRequest(serverRequest, serverResponse)) {
            handle(serverRequest, serverResponse);
        }
    }

    @Override
    public void settingsReceived(ChannelHandlerContext ctx, Http2Settings http2Settings) throws Exception {
        logger.log(Level.INFO, "settings received");
    }
}
