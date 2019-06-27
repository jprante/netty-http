package org.xbib.netty.http.server.transport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AttributeKey;

import java.io.IOException;

public interface Transport {

    AttributeKey<Transport> TRANSPORT_ATTRIBUTE_KEY = AttributeKey.valueOf("transport");

    void requestReceived(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws IOException;

    void requestReceived(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, Integer sequenceId) throws IOException;

    void settingsReceived(ChannelHandlerContext ctx, Http2Settings http2Settings) throws Exception;

    void exceptionReceived(ChannelHandlerContext ctx, Throwable throwable) throws IOException;
}
