package org.xbib.netty.http.server.transport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

abstract class BaseServerTransport implements ServerTransport {

    private static final Logger logger = Logger.getLogger(BaseServerTransport.class.getName());

    static final AtomicInteger requestCounter = new AtomicInteger();

    protected final Server server;

    BaseServerTransport(Server server) {
        this.server = server;
    }

    @Override
    public void exceptionReceived(ChannelHandlerContext ctx, Throwable throwable) {
        logger.log(Level.WARNING, throwable.getMessage(), throwable);
    }

    /**
     * Accepts a request, performing various validation checks
     * and required special header handling, possibly returning an
     * appropriate response.
     *
     * @param serverRequest  the request
     * @param serverResponse the response
     * @return whether further processing should be performed
     */
    static boolean acceptRequest(ServerRequest serverRequest, ServerResponse serverResponse) {
        HttpHeaders reqHeaders = serverRequest.getRequest().headers();
        HttpVersion version = serverRequest.getNamedServer().getHttpAddress().getVersion();
        switch (version.majorVersion()) {
            case 1:
            case 2:
                if (!reqHeaders.contains(HttpHeaderNames.HOST)) {
                    // RFC2616#14.23: missing Host header gets 400
                    serverResponse.writeError(HttpResponseStatus.BAD_REQUEST, "missing 'Host' header");
                    return false;
                }
                // return a continue response before reading body
                String expect = reqHeaders.get(HttpHeaderNames.EXPECT);
                if (expect != null) {
                    if (expect.equalsIgnoreCase("100-continue")) {
                        //ServerResponse tempResp = new ServerResponse(serverResponse);
                        //tempResp.sendHeaders(100);
                    } else {
                        // RFC2616#14.20: if unknown expect, send 417
                        serverResponse.writeError(HttpResponseStatus.EXPECTATION_FAILED);
                        return false;
                    }
                }
                break;
            default:
                serverResponse.writeError(HttpResponseStatus.BAD_REQUEST, "Unknown version: " + version);
                return false;
        }
        return true;
    }

    /**
     * Handles a request according to the request method.
     *
     * @param serverRequest  the request
     * @param serverResponse the response (into which the response is written)
     * @throws IOException if and error occurs
     */
    static void handle(HttpServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        serverRequest.getNamedServer().execute(serverRequest, serverResponse);
    }
}
