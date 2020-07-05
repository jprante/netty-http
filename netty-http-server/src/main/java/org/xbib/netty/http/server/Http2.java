package org.xbib.netty.http.server;

import org.xbib.netty.http.server.api.ServerProtocolProvider;
import org.xbib.netty.http.server.handler.http2.Http2ChannelInitializer;
import org.xbib.netty.http.server.transport.Http2Transport;

public class Http2 implements ServerProtocolProvider<Http2ChannelInitializer, Http2Transport> {

    @Override
    public boolean supportsMajorVersion(int majorVersion) {
        return majorVersion == 2;
    }

    @Override
    public Class<Http2ChannelInitializer> initializerClass() {
        return Http2ChannelInitializer.class;
    }

    @Override
    public Class<Http2Transport> transportClass() {
        return Http2Transport.class;
    }
}
