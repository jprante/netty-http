package org.xbib.netty.http.server.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpUtil;
import org.xbib.net.QueryParameters;
import org.xbib.net.URL;
import org.xbib.netty.http.common.HttpParameters;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.endpoint.EndpointInfo;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code HttpServerRequest} class encapsulates a single request. There must be a default constructor.
 */
public class HttpServerRequest implements ServerRequest {

    private static final String PATH_SEPARATOR = "/";

    private static final CharSequence APPLICATION_FORM_URL_ENCODED = "application/x-www-form-urlencoded";

    private ChannelHandlerContext ctx;

    private List<String> context;

    private String contextPath;

    private Map<String, String> pathParameters = new LinkedHashMap<>();

    private FullHttpRequest httpRequest;

    private EndpointInfo info;

    private HttpParameters parameters;

    private URL url;

    private Integer sequenceId;

    private Integer streamId;

    private Integer requestId;

    private SSLSession sslSession;

    public void setChannelHandlerContext(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    public ChannelHandlerContext getChannelHandlerContext() {
        return ctx;
    }

    public void setRequest(FullHttpRequest fullHttpRequest) {
        this.httpRequest = fullHttpRequest;
        this.info = new EndpointInfo(this);
    }

    public FullHttpRequest getRequest() {
        return httpRequest;
    }

    @Override
    public URL getURL() {
        return url;
    }

    @Override
    public EndpointInfo getEndpointInfo() {
        return info;
    }

    @Override
    public void setContext(List<String> context) {
        this.context = context;
        this.contextPath = context != null ? PATH_SEPARATOR + String.join(PATH_SEPARATOR, context) : null;
    }

    @Override
    public List<String> getContext() {
        return context;
    }

    @Override
    public String getContextPath() {
        return contextPath;
    }

    @Override
    public String getEffectiveRequestPath() {
        String path = getEndpointInfo().getPath();
        String effective = contextPath != null && !PATH_SEPARATOR.equals(contextPath) && path.startsWith(contextPath) ?
                path.substring(contextPath.length()) : path;
        return effective.isEmpty() ? PATH_SEPARATOR : effective;
    }

    @Override
    public void addPathParameter(String key, String value) throws IOException {
        pathParameters.put(key, value);
        parameters.add(key, value);
    }

    @Override
    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    @Override
    public HttpMethod getMethod() {
        return httpRequest.method();
    }

    @Override
    public HttpHeaders getHeaders() {
        return httpRequest.headers();
    }

    @Override
    public void createParameters() throws IOException {
        try {
            HttpParameters httpParameters = new HttpParameters();
            URL.Builder builder = URL.builder().path(getRequest().uri());
            this.url = builder.build();
            QueryParameters queryParameters = url.getQueryParams();
            ByteBuf byteBuf = httpRequest.content();
            if (APPLICATION_FORM_URL_ENCODED.equals(HttpUtil.getMimeType(httpRequest)) && byteBuf != null) {
                String content = byteBuf.toString(HttpUtil.getCharset(httpRequest, StandardCharsets.ISO_8859_1));
                queryParameters.addPercentEncodedBody(content);
            }
            for (QueryParameters.Pair<String, String> pair : queryParameters) {
                httpParameters.add(pair.getFirst(), pair.getSecond());
            }
            this.parameters = httpParameters;
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

    public void setSession(SSLSession sslSession) {
        this.sslSession = sslSession;
    }

    @Override
    public SSLSession getSession() {
        return sslSession;
    }

    @Override
    public ByteBuf getContent() {
        return httpRequest.content();
    }

    public String toString() {
        return "ServerRequest[request=" + httpRequest + "]";
    }
}
