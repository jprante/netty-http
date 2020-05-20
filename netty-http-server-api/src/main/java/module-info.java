module org.xbib.netty.http.server.api {
    exports org.xbib.netty.http.server.api;
    exports org.xbib.netty.http.server.api.annotation;
    requires org.xbib.net.url;
    requires org.xbib.netty.http.common;
    requires io.netty.buffer;
    requires io.netty.common;
    requires io.netty.handler;
    requires io.netty.transport;
    requires io.netty.codec.http;
    requires io.netty.codec.http2;
}
