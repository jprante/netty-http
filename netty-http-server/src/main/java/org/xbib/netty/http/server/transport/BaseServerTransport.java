package org.xbib.netty.http.server.transport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.context.ContextHandler;
import org.xbib.netty.http.server.context.VirtualServer;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

abstract class BaseServerTransport implements ServerTransport {

    private static final Logger logger = Logger.getLogger(BaseServerTransport.class.getName());

    protected static final AtomicInteger requestCounter = new AtomicInteger();

    private static final List<String> METHODS = Arrays.asList("GET", "HEAD", "OPTIONS");

    protected final Server server;

    protected BaseServerTransport(Server server) {
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
    protected static boolean acceptRequest(ServerRequest serverRequest, ServerResponse serverResponse) {
        HttpHeaders reqHeaders = serverRequest.getRequest().headers();
        HttpVersion version = serverRequest.getHttpAddress().getVersion();
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
    protected static void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        String method = serverRequest.getRequest().method().name();
        String path = serverRequest.getRequest().uri();
        VirtualServer virtualServer = serverRequest.getVirtualServer();
        VirtualServer.ContextPath contextPath = virtualServer.getContextPath(path);
        serverRequest.setContextPath(contextPath.getHook());
        Map<String, ContextHandler> methodHandlerMap = contextPath.getContextInfo().getMethodHandlerMap();
        // RFC 2616#5.1.1 - GET and HEAD must be supported
        if (method.equals("GET") || method.equals("HEAD") || methodHandlerMap.containsKey(method)) {
            ContextHandler handler = methodHandlerMap.get(method);
            if (handler == null) {
                serverResponse.writeError(HttpResponseStatus.NOT_FOUND);
            } else {
                handler.serve(serverRequest, serverResponse);
            }
        } else {
            Set<String> methods = new LinkedHashSet<>(METHODS);
            // "*" is a special server-wide (no-context) request supported by OPTIONS
            boolean isServerOptions = path.equals("*") && method.equals("OPTIONS");
            methods.addAll(isServerOptions ? virtualServer.getMethods() : methodHandlerMap.keySet());
            serverResponse.setHeader(HttpHeaderNames.ALLOW, String.join(", ", methods));
            if (method.equals("OPTIONS")) { // default OPTIONS handler
                serverResponse.setHeader(HttpHeaderNames.CONTENT_LENGTH, "0"); // RFC2616#9.2
                serverResponse.write(HttpResponseStatus.OK);
            } else if (virtualServer.getMethods().contains(method)) {
                serverResponse.write(HttpResponseStatus.METHOD_NOT_ALLOWED); // supported by server, but not this context (nor built-in)
            } else {
                serverResponse.writeError(HttpResponseStatus.NOT_IMPLEMENTED); // unsupported method
            }
        }
    }
}
