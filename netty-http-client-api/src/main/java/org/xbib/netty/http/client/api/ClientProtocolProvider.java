package org.xbib.netty.http.client.api;

import org.xbib.netty.http.common.HttpChannelInitializer;

public interface ClientProtocolProvider<C extends HttpChannelInitializer, T extends ClientTransport> {

    boolean supportsMajorVersion(int majorVersion);

    Class<C> initializerClass();

    Class<T> transportClass();
}
