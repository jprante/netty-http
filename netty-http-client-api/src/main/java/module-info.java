module org.xbib.netty.http.client.api {
    exports org.xbib.netty.http.client.api;
    requires org.xbib.netty.http.common;
    requires org.xbib.net;
    requires io.netty.buffer;
    requires io.netty.common;
    requires io.netty.transport;
    requires io.netty.codec.http;
    requires io.netty.codec.http2;
}
