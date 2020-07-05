package org.xbib.netty.http.server.api;

import org.xbib.netty.http.common.HttpChannelInitializer;

public interface ServerProtocolProvider<C extends HttpChannelInitializer, T extends ServerTransport> {

    boolean supportsMajorVersion(int majorVersion);

    Class<C> initializerClass();

    Class<T> transportClass();
}
