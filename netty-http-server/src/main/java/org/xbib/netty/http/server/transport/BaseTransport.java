package org.xbib.netty.http.server.transport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.endpoint.NamedServer;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

abstract class BaseTransport implements Transport {

    private static final Logger logger = Logger.getLogger(BaseTransport.class.getName());

    static final AtomicInteger requestCounter = new AtomicInteger();

    protected final Server server;

    BaseTransport(Server server) {
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
     * @param namedServer the named server
     * @param serverRequest  the request
     * @param serverResponse the response
     * @return whether further processing should be performed
     */
    static boolean acceptRequest(NamedServer namedServer, ServerRequest serverRequest, ServerResponse serverResponse) {
        HttpHeaders reqHeaders = serverRequest.getRequest().headers();
        HttpVersion version = namedServer.getHttpAddress().getVersion();
        if (version.majorVersion() == 1 || version.majorVersion() == 2) {
            if (!reqHeaders.contains(HttpHeaderNames.HOST)) {
                // RFC2616#14.23: missing Host header gets 400
                ServerResponse.write(serverResponse,
                        HttpResponseStatus.BAD_REQUEST, "application/octet-stream", "missing 'Host' header");
                return false;
            }
            // return a continue response before reading body
            String expect = reqHeaders.get(HttpHeaderNames.EXPECT);
            if (expect != null) {
                if ("100-continue".equalsIgnoreCase(expect)) {
                    //ServerResponse tempResp = new ServerResponse(serverResponse);
                    //tempResp.sendHeaders(100);
                } else {
                    // RFC2616#14.20: if unknown expect, send 417
                    ServerResponse.write(serverResponse, HttpResponseStatus.EXPECTATION_FAILED);
                    return false;
                }
            }
        } else {
            ServerResponse.write(serverResponse, HttpResponseStatus.BAD_REQUEST,
                    "application/octet-stream", "unsupported HTTP version: " + version);
            return false;
        }
        return true;
    }

    /**
     * Handles a request according to the request method.
     * @param namedServer the named server
     * @param serverRequest  the request
     * @param serverResponse the response (into which the response is written)
     * @throws IOException if and error occurs
     */
    static void handle(NamedServer namedServer, HttpServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        // create server URL and parse parameters from query string, path, and parse body, if exists
        serverRequest.createParameters();
        namedServer.execute(serverRequest, serverResponse);
    }
}
