package org.xbib.netty.http.client;

import org.xbib.netty.http.client.api.ProtocolProvider;
import org.xbib.netty.http.client.handler.http2.Http2ChannelInitializer;
import org.xbib.netty.http.client.transport.Http2Transport;

public class Http2Provider implements ProtocolProvider<Http2ChannelInitializer, Http2Transport> {

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
