package org.xbib.netty.http.client.api;

public interface ProtocolProvider<C extends HttpChannelInitializer, T extends Transport> {

    boolean supportsMajorVersion(int majorVersion);

    Class<C> initializerClass();

    Class<T> transportClass();
}
