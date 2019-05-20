package org.xbib.netty.http.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import org.xbib.netty.http.server.endpoint.NamedServer;

import java.util.List;
import java.util.Map;

public interface ServerRequest {

    NamedServer getNamedServer();

    ChannelHandlerContext getChannelHandlerContext();

    FullHttpRequest getRequest();

    void setContext(List<String> context);

    List<String> getContext();

    void setRawParameters(Map<String, String> rawParameters);

    Map<String, String> getRawParameters();

    String getContextPath();

    String getEffectiveRequestPath();

    Integer getSequenceId();

    Integer streamId();

    Integer requestId();
}
