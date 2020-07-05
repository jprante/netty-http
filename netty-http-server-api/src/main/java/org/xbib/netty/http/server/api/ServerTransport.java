package org.xbib.netty.http.server.api;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AttributeKey;
import org.xbib.netty.http.common.Transport;
import java.io.IOException;

public interface ServerTransport extends Transport {

    AttributeKey<ServerTransport> TRANSPORT_ATTRIBUTE_KEY = AttributeKey.valueOf("transport");

    void requestReceived(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, Integer sequenceId) throws IOException;

    void settingsReceived(ChannelHandlerContext ctx, Http2Settings http2Settings) throws Exception;

    void exceptionReceived(ChannelHandlerContext ctx, Throwable throwable) throws IOException;
}
