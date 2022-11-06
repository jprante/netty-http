package org.xbib.netty.http.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpUtil;
import org.xbib.datastructures.common.Pair;
import org.xbib.net.Parameter;
import org.xbib.net.ParameterBuilder;
import org.xbib.net.PercentDecoder;
import org.xbib.net.URL;
import org.xbib.netty.http.common.HttpParameters;
import org.xbib.netty.http.server.api.Domain;
import org.xbib.netty.http.server.api.Endpoint;
import org.xbib.netty.http.server.api.EndpointResolver;
import org.xbib.netty.http.server.api.ServerRequest;
import javax.net.ssl.SSLSession;
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
 */
public class HttpServerRequest implements ServerRequest {

    private static final Logger logger = Logger.getLogger(HttpServerRequest.class.getName());

    private static final String PATH_SEPARATOR = "/";

    private final Builder builder;

    private final InetSocketAddress localAddress;

    private final InetSocketAddress remoteAddress;

    private final FullHttpRequest httpRequest;

    private final URL baseURL;

    private final URL contextURL;

    private final URL url;

    private final List<String> context;

    private final String contextPath;

    private final HttpParameters parameters;

    private final Map<String, String> pathParameters;

    private final String effectiveRequestPath;

    private final Integer sequenceId;

    private final Integer streamId;

    private final Long requestId;

    private final SSLSession sslSession;

    private final Domain<? extends EndpointResolver<? extends Endpoint<?>>> domain;

    private final EndpointResolver<? extends Endpoint<?>> endpointResolver;

    private final Endpoint<?> endpoint;

    private HttpServerRequest(Builder builder) {
        this.builder = builder;
        this.localAddress = builder.localAddress;
        this.remoteAddress = builder.remoteAddress;
        this.httpRequest = builder.fullHttpRequest;
        this.baseURL = builder.baseURL;
        this.contextURL = builder.contextURL;
        this.url = builder.url;
        this.context = builder.context;
        this.contextPath = builder.contextPath;
        this.parameters = builder.parameters;
        this.pathParameters = builder.pathParameters;
        this.effectiveRequestPath = builder.effectiveRequestPath;
        this.sequenceId = builder.sequenceId;
        this.streamId = builder.streamId;
        this.requestId = builder.requestId;
        this.sslSession = builder.sslSession;
        this.domain = builder.domain;
        this.endpointResolver = builder.endpointResolver;
        this.endpoint = builder.endpoint;
    }

    public Builder getBuilder() {
        return builder;
    }

    public static Builder builder() {
        return new Builder();
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
    public HttpMethod getMethod() {
        return httpRequest.method();
    }

    @Override
    public String getRequestURI() {
        return httpRequest.uri();
    }

    @Override
    public HttpHeaders getHeaders() {
        return httpRequest.headers();
    }

    @Override
    public String getHeader(String name) {
        return httpRequest.headers().get(name);
    }

    @Override
    public ByteBuf getContent() {
        return httpRequest.content();
    }

    @Override
    public String getContent(Charset charset) {
        return httpRequest.content().toString(charset);
    }

    @Override
    public ByteBufInputStream getInputStream() {
        return new ByteBufInputStream(httpRequest.content(), true);
    }

    @Override
    public URL getBaseURL() {
        return baseURL;
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
    public URL getContextURL() {
        return contextURL;
    }

    @Override
    public String getEffectiveRequestPath() {
        return effectiveRequestPath;
    }

    @Override
    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    @Override
    public Domain<? extends EndpointResolver<? extends Endpoint<?>>> getDomain() {
        return domain;
    }

    @Override
    public EndpointResolver<? extends Endpoint<?>> getEndpointResolver() {
        return endpointResolver;
    }

    @Override
    public Endpoint<?> getEndpoint() {
        return endpoint;
    }

    @Override
    public URL getURL() {
        return url;
    }

    @Override
    public HttpParameters getParameters() {
        return parameters;
    }

    @Override
    public Integer getSequenceId() {
        return sequenceId;
    }

    @Override
    public Integer getStreamId() {
        return streamId;
    }

    @Override
    public Long getRequestId() {
        return requestId;
    }

    @Override
    public SSLSession getSession() {
        return sslSession;
    }

    @Override
    public String toString() {
        return "ServerRequest[request=" + httpRequest + "]";
    }

    public static class Builder implements ServerRequest.Builder {

        private final Map<String, String> pathParameters;

        private InetSocketAddress localAddress;

        private InetSocketAddress remoteAddress;

        private FullHttpRequest fullHttpRequest;

        private URL baseURL;

        private URL contextURL;

        private URL url;

        private List<String> context;

        private String contextPath;

        private String effectiveRequestPath;

        private HttpParameters parameters;

        private Domain<? extends EndpointResolver<? extends Endpoint<?>>> domain;

        private EndpointResolver<? extends Endpoint<?>> endpointResolver;

        private Endpoint<?> endpoint;

        private Integer sequenceId;

        private Integer streamId;

        private Long requestId;

        private SSLSession sslSession;

        private Builder() {
            this.pathParameters = new LinkedHashMap<>();
        }

        public Builder setLocalAddress(InetSocketAddress localAddress) {
            this.localAddress = localAddress;
            return this;
        }

        public Builder setRemoteAddress(InetSocketAddress remoteAddress) {
            this.remoteAddress = remoteAddress;
            return this;
        }

        public Builder setHttpRequest(FullHttpRequest fullHttpRequest) {
            this.fullHttpRequest = fullHttpRequest;
            return this;
        }

        public String getRequestURI() {
            return fullHttpRequest.uri();
        }

        public HttpMethod getMethod() {
            return fullHttpRequest.method();
        }

        public HttpHeaders getHeaders() {
            return fullHttpRequest.headers();
        }

        public Builder setBaseURL(URL baseURL) {
            this.baseURL = baseURL;
            return this;
        }

        public Builder setContext(List<String> context) {
            this.context = context;
            this.contextPath = context != null ? PATH_SEPARATOR + String.join(PATH_SEPARATOR, context) : null;
            this.contextURL = baseURL.resolve(contextPath != null ? contextPath + "/" : "");
            String path = extractPath(fullHttpRequest.uri());
            String effective = contextPath != null && !PATH_SEPARATOR.equals(contextPath) && path.startsWith(contextPath) ?
                    path.substring(contextPath.length()) : path;
            this.effectiveRequestPath = effective.isEmpty() ? PATH_SEPARATOR : effective;
            return this;
        }

        public String getEffectiveRequestPath() {
            return effectiveRequestPath;
        }

        public Builder addPathParameter(String key, String value) {
            pathParameters.put(key, value);
            //parameters.addRaw(key, value);
            return this;
        }

        public Builder setSequenceId(Integer sequenceId) {
            this.sequenceId = sequenceId;
            return this;
        }

        public Builder setStreamId(Integer streamId) {
            this.streamId = streamId;
            return this;
        }

        public Builder setRequestId(Long requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder setSession(SSLSession sslSession) {
            this.sslSession = sslSession;
            return this;
        }

        public Builder setDomain(Domain<? extends EndpointResolver<? extends Endpoint<?>>> domain) {
            this.domain = domain;
            return this;
        }

        public Builder setEndpointResolver(EndpointResolver<? extends Endpoint<?>> endpointResolver) {
            this.endpointResolver = endpointResolver;
            return this;
        }

        public Builder setEndpoint(Endpoint<?> endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public ServerRequest build() {
            // build URL and parameters
            Charset charset = HttpUtil.getCharset(fullHttpRequest, StandardCharsets.UTF_8);
            // creates path, query params, fragment
            this.url = URL.builder()
                    .charset(charset, CodingErrorAction.REPLACE)
                    .path(fullHttpRequest.uri()) // creates path, query params, fragment
                    .build();
            ParameterBuilder queryParameters = Parameter.builder();
                    //url.getQueryParams();
            CharSequence mimeType = HttpUtil.getMimeType(fullHttpRequest);
            ByteBuf byteBuf = fullHttpRequest.content();
            if (byteBuf != null) {
                if (fullHttpRequest.method().equals(HttpMethod.POST)) {
                    String params;
                    // https://www.w3.org/TR/html4/interact/forms.html#h-17.13.4
                    if (mimeType != null && HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString().equals(mimeType.toString())) {
                        Charset htmlCharset = HttpUtil.getCharset(fullHttpRequest, StandardCharsets.ISO_8859_1);
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
            this.parameters = new HttpParameters(mimeType, charset);
            for (Pair<String, Object> pair : queryParameters.build()) {
                try {
                    parameters.addRaw(percentDecoder.decode(pair.getKey()), percentDecoder.decode(pair.getValue().toString()));
                } catch (Exception e) {
                    // does not happen
                    throw new IllegalArgumentException(pair.toString());
                }
            }
            return new HttpServerRequest(this);
        }

        @Override
        public void release() {
            fullHttpRequest.release();
        }

        public ServerRequest applyTo(Server server) {
            URL baseURL = server.getBaseURL(fullHttpRequest.headers());
            setBaseURL(baseURL);
            Domain<? extends EndpointResolver<?>> domain = server.getDomain(baseURL);
            try {
                domain.handle(this, null);
            } catch (Throwable t) {
                logger.log(Level.SEVERE, t.getMessage(), t);
            }
            return build();
        }

        private String extractPath(String uri) {
            String path = uri;
            int pos = uri.lastIndexOf('#');
            path = pos >= 0 ? path.substring(0, pos) : path;
            pos = uri.lastIndexOf('?');
            path = pos >= 0 ? path.substring(0, pos) : path;
            return path;
        }
    }
}
