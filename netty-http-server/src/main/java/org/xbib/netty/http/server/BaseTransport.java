package org.xbib.netty.http.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpVersion;
import org.xbib.netty.http.server.api.ServerTransport;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BaseTransport implements ServerTransport {

    private static final Logger logger = Logger.getLogger(BaseTransport.class.getName());

    protected final Server server;

    protected BaseTransport(Server server) {
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
     * @param httpVersion the server HTTP version
     * @param reqHeaders  the request headers
     * @return whether further processing should be performed
     */
    protected static AcceptState acceptRequest(HttpVersion httpVersion, HttpHeaders reqHeaders) {
        if (httpVersion.majorVersion() == 1 || httpVersion.majorVersion() == 2) {
            if (!reqHeaders.contains(HttpHeaderNames.HOST)) {
                // RFC2616#14.23: missing Host header gets 400
                return AcceptState.MISSING_HOST_HEADER;
            }
            // return a continue response before reading body
            String expect = reqHeaders.get(HttpHeaderNames.EXPECT);
            if (expect != null) {
                if (!"100-continue".equalsIgnoreCase(expect)) {
                    // RFC2616#14.20: if unknown expect, send 417
                    return AcceptState.EXPECTATION_FAILED;
                }
            }
            return AcceptState.OK;
        } else {
            return AcceptState.UNSUPPORTED_HTTP_VERSION;
        }
    }
}
