package org.xbib.netty.http.server.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import org.xbib.net.QueryParameters;
import org.xbib.net.URL;
import org.xbib.netty.http.common.HttpParameters;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.endpoint.NamedServer;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@code HttpServerRequest} class encapsulates a single request. There must be a default constructor.
 */
public class HttpServerRequest implements ServerRequest {

    private static final Logger logger = Logger.getLogger(HttpServerRequest.class.getName());

    private static final String PATH_SEPARATOR = "/";

    private static final CharSequence APPLICATION_FORM_URL_ENCODED = "application/x-www-form-urlencoded";

    private NamedServer namedServer;

    private ChannelHandlerContext ctx;

    private List<String> context;

    private Map<String, String> pathParameters;

    private FullHttpRequest httpRequest;

    private HttpParameters parameters;

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

    public void setPathParameters(Map<String, String> pathParameters) {
        this.pathParameters = pathParameters;
    }

    @Override
    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    @Override
    public void createParameters() throws IOException {
        try {
            buildParameters();
        } catch (MalformedInputException | UnmappableCharacterException e) {
            throw new IOException(e);
        }
    }

    @Override
    public HttpParameters getParameters() {
        return parameters;
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

    private void buildParameters() throws MalformedInputException, UnmappableCharacterException {
        HttpParameters httpParameters = new HttpParameters();
        URL.Builder builder = URL.builder().path(getEffectiveRequestPath());
        if (pathParameters != null && !pathParameters.isEmpty()) {
            for (Map.Entry<String, String> entry : pathParameters.entrySet()) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }
        }
        QueryParameters queryParameters = builder.build().getQueryParams();
        ByteBuf byteBuf = httpRequest.content();
        if (APPLICATION_FORM_URL_ENCODED.equals(HttpUtil.getMimeType(httpRequest)) && byteBuf != null) {
            String content = byteBuf.toString(HttpUtil.getCharset(httpRequest, StandardCharsets.ISO_8859_1));
            queryParameters.addPercentEncodedBody(content);
        }
        for (QueryParameters.Pair<String, String> pair : queryParameters) {
            httpParameters.add(pair.getFirst(), pair.getSecond());
        }
        this.parameters = httpParameters;
    }

    public String toString() {
        return "ServerRequest[namedServer=" + namedServer +
                ",context=" + context +
                ",request=" + httpRequest +
                "]";
    }
}
