package org.xbib.netty.http.server.transport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpUtil;
import org.xbib.net.Pair;
import org.xbib.net.PercentDecoder;
import org.xbib.net.QueryParameters;
import org.xbib.net.URL;
import org.xbib.netty.http.common.HttpParameters;
import org.xbib.netty.http.server.api.ServerRequest;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@code HttpServerRequest} class encapsulates a single request.
 * There must be a default constructor.
 */
public class HttpServerRequest implements ServerRequest {

    private static final Logger logger = Logger.getLogger(HttpServerRequest.class.getName());

    private static final String PATH_SEPARATOR = "/";

    private final FullHttpRequest httpRequest;

    private final InetSocketAddress localAddress;

    private final InetSocketAddress remoteAddress;

    private final Map<String, String> pathParameters;

    private List<String> context;

    private String contextPath;

    private HttpParameters parameters;

    private URL url;

    private Integer sequenceId;

    private Integer streamId;

    private Long requestId;

    private SSLSession sslSession;

    public HttpServerRequest(FullHttpRequest fullHttpRequest) {
        this( fullHttpRequest ,null, null);
    }

    public HttpServerRequest(FullHttpRequest fullHttpRequest,
                             InetSocketAddress localAddress,
                             InetSocketAddress remoteAddress) {
        this.httpRequest = fullHttpRequest != null ? fullHttpRequest.retainedDuplicate() : null;
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
        this.pathParameters = new LinkedHashMap<>();
    }

    void handleParameters() {
        Charset charset = HttpUtil.getCharset(httpRequest, StandardCharsets.UTF_8);
        this.url = URL.builder()
                .charset(charset, CodingErrorAction.REPLACE)
                .path(httpRequest.uri()) // creates path, query params, fragment
                .build();
        QueryParameters queryParameters = url.getQueryParams();
        CharSequence mimeType = HttpUtil.getMimeType(httpRequest);
        ByteBuf byteBuf = httpRequest.content();
        if (byteBuf != null) {
            if (httpRequest.method().equals(HttpMethod.POST)) {
                String params;
                // https://www.w3.org/TR/html4/interact/forms.html#h-17.13.4
                if (mimeType != null && HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString().equals(mimeType.toString())) {
                    Charset htmlCharset = HttpUtil.getCharset(httpRequest, StandardCharsets.ISO_8859_1);
                    params = byteBuf.toString(htmlCharset).replace('+', ' ');
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "html form, charset = " + htmlCharset + " param body = " + params);
                    }
                    queryParameters.addPercentEncodedBody(params);
                }
            }
        }
        // copy to HTTP parameters but percent-decoded (looks very clumsy)
        PercentDecoder percentDecoder = new PercentDecoder(charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE));
        HttpParameters httpParameters = new HttpParameters(mimeType, charset);
        for (Pair<String, String> pair : queryParameters) {
            try {
                httpParameters.addRaw(percentDecoder.decode(pair.getFirst()), percentDecoder.decode(pair.getSecond()));
            } catch (Exception e) {
                // does not happen
                throw new IllegalArgumentException(pair.toString());
            }
        }
        this.parameters = httpParameters;
    }

    @Override
    public URL getURL() {
        return url;
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
        String path = extractPath(getRequestURI());
        String effective = contextPath != null && !PATH_SEPARATOR.equals(contextPath) && path.startsWith(contextPath) ?
                path.substring(contextPath.length()) : path;
        return effective.isEmpty() ? PATH_SEPARATOR : effective;
    }

    @Override
    public void addPathParameter(String key, String value) throws IOException {
        pathParameters.put(key, value);
        parameters.addRaw(key, value);
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
    public HttpParameters getParameters() {
        return parameters;
    }

    @Override
    public String getRequestURI() {
        return httpRequest.uri();
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
    public Integer getStreamId() {
        return streamId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    @Override
    public Long getRequestId() {
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
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public ByteBuf getContent() {
        return httpRequest.content();
    }

    @Override
    public ByteBufInputStream getInputStream() {
        return new ByteBufInputStream(getContent(), true);
    }

    public void release() {
        httpRequest.release();
    }

    public String toString() {
        return "ServerRequest[request=" + httpRequest + "]";
    }

    private static String extractPath(String uri) {
        String path = uri;
        int pos = uri.lastIndexOf('#');
        path = pos >= 0 ? path.substring(0, pos) : path;
        pos = uri.lastIndexOf('?');
        path = pos >= 0 ? path.substring(0, pos) : path;
        return path;
    }
}
