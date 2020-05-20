module org.xbib.netty.http.server {
    exports org.xbib.netty.http.server;
    exports org.xbib.netty.http.server.cookie;
    exports org.xbib.netty.http.server.endpoint;
    exports org.xbib.netty.http.server.endpoint.service;
    exports org.xbib.netty.http.server.handler;
    exports org.xbib.netty.http.server.handler.http;
    exports org.xbib.netty.http.server.handler.http2;
    exports org.xbib.netty.http.server.handler.stream;
    exports org.xbib.netty.http.server.transport;
    exports org.xbib.netty.http.server.util;
    requires org.xbib.netty.http.server.api;
    requires org.xbib.netty.http.common;
    requires org.xbib.net.url;
    requires io.netty.buffer;
    requires io.netty.common;
    requires io.netty.handler;
    requires io.netty.transport;
    requires io.netty.codec.http;
    requires java.logging;
    provides org.xbib.netty.http.server.api.ProtocolProvider with
            org.xbib.netty.http.server.Http1Provider,
            org.xbib.netty.http.server.Http2Provider;
}
