package org.xbib.netty.http.server.api;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.xbib.net.URL;
import org.xbib.netty.http.common.HttpParameters;
import javax.net.ssl.SSLSession;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

public interface ServerRequest {

    Builder getBuilder();

    InetSocketAddress getLocalAddress();

    InetSocketAddress getRemoteAddress();

    URL getURL();

    List<String> getContext();

    Map<String, String> getPathParameters();

    String getRequestURI();

    HttpMethod getMethod();

    HttpHeaders getHeaders();

    String getHeader(String name);

    HttpParameters getParameters();

    String getContextPath();

    String getEffectiveRequestPath();

    Integer getSequenceId();

    Integer getStreamId();

    Long getRequestId();

    ByteBuf getContent();

    String getContent(Charset charset);

    ByteBufInputStream getInputStream();

    SSLSession getSession();

    URL getBaseURL();

    URL getContextURL();

    Domain<? extends EndpointResolver<? extends Endpoint<?>>> getDomain();

    EndpointResolver<? extends Endpoint<?>> getEndpointResolver();

    Endpoint<?> getEndpoint();

    interface Builder {

        String getRequestURI();

        HttpMethod getMethod();

        HttpHeaders getHeaders();

        String getEffectiveRequestPath();

        Builder setBaseURL(URL baseURL);

        Builder setDomain(Domain<? extends EndpointResolver<? extends Endpoint<?>>> domain);

        Builder setEndpointResolver(EndpointResolver<? extends Endpoint<?>> endpointResolver);

        Builder setEndpoint(Endpoint<?> endpoint);

        Builder setContext(List<String> context);

        Builder addPathParameter(String key, String value);

        ServerRequest build();

        void release();
    }
}
