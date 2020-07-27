package org.xbib.netty.http.server.protocol.http2;

import org.xbib.netty.http.server.api.ServerProtocolProvider;

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
