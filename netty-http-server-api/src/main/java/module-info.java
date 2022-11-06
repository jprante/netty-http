module org.xbib.netty.http.server.api {
    exports org.xbib.netty.http.server.api;
    requires org.xbib.netty.http.common;
    requires org.xbib.net;
    requires io.netty.buffer;
    requires io.netty.common;
    requires io.netty.handler;
    requires io.netty.transport;
    requires io.netty.codec.http;
    requires io.netty.codec.http2;
}
