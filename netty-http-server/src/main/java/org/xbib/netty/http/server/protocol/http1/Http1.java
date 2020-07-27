package org.xbib.netty.http.server.protocol.http1;

import org.xbib.netty.http.server.api.ServerProtocolProvider;

public class Http1 implements ServerProtocolProvider<Http1ChannelInitializer, Http1Transport> {

    @Override
    public boolean supportsMajorVersion(int majorVersion) {
        return majorVersion == 1;
    }

    @Override
    public Class<Http1ChannelInitializer> initializerClass() {
        return Http1ChannelInitializer.class;
    }

    @Override
    public Class<Http1Transport> transportClass() {
        return Http1Transport.class;
    }
}
