package org.xbib.netty.http.server.transport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.endpoint.NamedServer;

import java.util.List;
import java.util.Map;

/**
 * The {@code HttpServerRequest} class encapsulates a single request. There must be a default constructor.
 */
public class HttpServerRequest implements ServerRequest {

    private static final String PATH_SEPARATOR = "/";

    private NamedServer namedServer;

    private ChannelHandlerContext ctx;

    private List<String> context;

    private Map<String, String> rawParameters;

    private FullHttpRequest httpRequest;

    private Integer sequenceId;

    private Integer streamId;

    private Integer requestId;

    public void setNamedServer(NamedServer namedServer) {
        this.namedServer = namedServer;
    }

    @Override
    public NamedServer getNamedServer() {
        return namedServer;
    }

    public void setChannelHandlerContext(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public ChannelHandlerContext getChannelHandlerContext() {
        return ctx;
    }

    public void setRequest(FullHttpRequest fullHttpRequest) {
        this.httpRequest = fullHttpRequest;
    }

    @Override
    public FullHttpRequest getRequest() {
        return httpRequest;
    }

    public void setContext(List<String> context) {
        this.context = context;
    }

    @Override
    public List<String> getContext() {
        return context;
    }

    @Override
    public String getContextPath() {
        return String.join(PATH_SEPARATOR, context);
    }

    @Override
    public String getEffectiveRequestPath() {
        String uri = httpRequest.uri();
        return context != null && !context.isEmpty() && uri.length() > 1 ?
                uri.substring(getContextPath().length() + 2) : uri;
    }

    public void setRawParameters(Map<String, String> rawParameters) {
        this.rawParameters = rawParameters;
    }

    @Override
    public Map<String, String> getRawParameters() {
        return rawParameters;
    }

    public void setSequenceId(Integer sequenceId) {
        this.sequenceId = sequenceId;
    }

    @Override
    public Integer getSequenceId() {
        return sequenceId;
    }

    public void setStreamId(Integer streamId) {
        this.streamId = streamId;
    }

    @Override
    public Integer streamId() {
        return streamId;
    }

    public void setRequestId(Integer requestId) {
        this.requestId = requestId;
    }

    @Override
    public Integer requestId() {
        return requestId;
    }

    public String toString() {
        return "ServerRequest[namedServer=" + namedServer +
                ",context=" + context +
                ",request=" + httpRequest +
                "]";
    }
}
