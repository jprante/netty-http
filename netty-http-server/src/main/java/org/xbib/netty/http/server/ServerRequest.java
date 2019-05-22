package org.xbib.netty.http.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import org.xbib.netty.http.common.HttpParameters;
import org.xbib.netty.http.server.endpoint.NamedServer;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ServerRequest {

    NamedServer getNamedServer();

    ChannelHandlerContext getChannelHandlerContext();

    FullHttpRequest getRequest();

    void setContext(List<String> context);

    List<String> getContext();

    void setPathParameters(Map<String, String> rawParameters);

    Map<String, String> getPathParameters();

    void createParameters() throws IOException;

    HttpParameters getParameters();

    String getContextPath();

    String getEffectiveRequestPath();

    Integer getSequenceId();

    Integer streamId();

    Integer requestId();
}
