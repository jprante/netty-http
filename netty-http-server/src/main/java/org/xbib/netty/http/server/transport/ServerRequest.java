package org.xbib.netty.http.server.transport;

import io.netty.handler.codec.http.FullHttpRequest;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.context.VirtualServer;

/**
 * The {@code ServerRequest} class encapsulates a single request.
 */
public class ServerRequest {

    private final VirtualServer virtualServer;

    private final HttpAddress httpAddress;

    private final FullHttpRequest httpRequest;

    private final Integer sequenceId;

    private final Integer streamId;

    private final Integer requestId;

    private String contextPath;

    public ServerRequest(VirtualServer virtualServer, HttpAddress httpAddress,
                         FullHttpRequest httpRequest, Integer sequenceId, Integer streamId, Integer requestId) {
        this.virtualServer = virtualServer;
        this.httpAddress = httpAddress;
        this.httpRequest = httpRequest;
        this.sequenceId = sequenceId;
        this.streamId = streamId;
        this.requestId = requestId;
    }

    public VirtualServer getVirtualServer() {
        return virtualServer;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public String getContextPath() {
        return contextPath;
    }

    public String getRequestPath() {
        return contextPath != null ? httpRequest.uri().substring(contextPath.length()) : httpRequest.uri();
    }

    public HttpAddress getHttpAddress() {
        return httpAddress;
    }

    public FullHttpRequest getRequest() {
        return httpRequest;
    }

    public Integer getSequenceId() {
        return sequenceId;
    }

    public Integer streamId() {
        return streamId;
    }

    public Integer requestId() {
        return requestId;
    }
}
