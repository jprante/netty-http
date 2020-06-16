module org.xbib.netty.http.client {
    exports org.xbib.netty.http.client;
    exports org.xbib.netty.http.client.cookie;
    exports org.xbib.netty.http.client.handler.http;
    exports org.xbib.netty.http.client.handler.http2;
    exports org.xbib.netty.http.client.pool;
    exports org.xbib.netty.http.client.retry;
    exports org.xbib.netty.http.client.transport;
    requires transitive org.xbib.netty.http.client.api;
    requires io.netty.buffer;
    requires io.netty.common;
    requires io.netty.codec.http;
    requires io.netty.codec.http2;
    requires io.netty.handler;
    requires io.netty.handler.proxy;
    requires io.netty.transport;
    requires java.logging;
    provides org.xbib.netty.http.client.api.ProtocolProvider with
            org.xbib.netty.http.client.Http1Provider,
            org.xbib.netty.http.client.Http2Provider;
}
