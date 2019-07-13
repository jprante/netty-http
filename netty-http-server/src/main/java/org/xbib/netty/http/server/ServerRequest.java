package org.xbib.netty.http.server;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.xbib.net.URL;
import org.xbib.netty.http.common.HttpParameters;
import org.xbib.netty.http.server.endpoint.EndpointInfo;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ServerRequest {

    URL getURL();

    EndpointInfo getEndpointInfo();

    void setContext(List<String> context);

    List<String> getContext();

    void addPathParameter(String key, String value) throws IOException;

    void createParameters() throws IOException;

    Map<String, String> getPathParameters();

    HttpMethod getMethod();

    HttpHeaders getHeaders();

    HttpParameters getParameters();

    String getContextPath();

    String getEffectiveRequestPath();

    Integer getSequenceId();

    Integer streamId();

    Integer requestId();

    SSLSession getSession();

    ByteBuf getContent();

}
