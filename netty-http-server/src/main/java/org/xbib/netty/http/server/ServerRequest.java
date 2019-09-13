package org.xbib.netty.http.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.xbib.net.URL;
import org.xbib.netty.http.common.HttpParameters;
import org.xbib.netty.http.server.endpoint.HttpEndpointDescriptor;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

public interface ServerRequest {

    URL getURL();

    HttpEndpointDescriptor getEndpointDescriptor();

    void setContext(List<String> context);

    List<String> getContext();

    void addPathParameter(String key, String value) throws IOException;

    Map<String, String> getPathParameters();

    String getRequestURI();

    HttpMethod getMethod();

    HttpHeaders getHeaders();

    HttpParameters getParameters();

    String getContextPath();

    String getEffectiveRequestPath();

    Integer getSequenceId();

    Integer getStreamId();

    Long getRequestId();

    ByteBuf getContent();

    ByteBufInputStream getInputStream();

    SSLSession getSession();

    InetSocketAddress getLocalAddress();

    InetSocketAddress getRemoteAddress();
}
