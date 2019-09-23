package org.xbib.netty.http.client;

import org.xbib.netty.http.client.api.ProtocolProvider;
import org.xbib.netty.http.client.handler.http.Http1ChannelInitializer;
import org.xbib.netty.http.client.transport.Http1Transport;

public class Http1Provider implements ProtocolProvider<Http1ChannelInitializer, Http1Transport> {

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
