module org.xbib.netty.http.client.api {
    exports org.xbib.netty.http.client.api;
    requires transitive org.xbib.netty.http.common;
    requires io.netty.buffer;
    requires io.netty.common;
    requires io.netty.codec.http;
    requires io.netty.codec.http2;
    requires io.netty.transport;
}
