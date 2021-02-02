module org.xbib.netty.http.server.api {
    exports org.xbib.netty.http.server.api;
    exports org.xbib.netty.http.server.api.annotation;
    exports org.xbib.netty.http.server.api.security;
    requires org.xbib.netty.http.common;
    requires org.xbib.net.url;
    requires io.netty.buffer;
    requires io.netty.codec.http;
}
