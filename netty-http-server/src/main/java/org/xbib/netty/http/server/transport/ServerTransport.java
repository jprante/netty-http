package org.xbib.netty.http.server.transport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AttributeKey;

import java.io.IOException;

public interface ServerTransport {

    AttributeKey<ServerTransport> TRANSPORT_ATTRIBUTE_KEY = AttributeKey.valueOf("transport");

    void requestReceived(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws IOException;

    void settingsReceived(ChannelHandlerContext ctx, Http2Settings http2Settings) throws Exception;

}
