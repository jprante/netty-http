module org.xbib.netty.http.common {
    exports org.xbib.netty.http.common;
    exports org.xbib.netty.http.common.cookie;
    exports org.xbib.netty.http.common.mime;
    exports org.xbib.netty.http.common.security;
    exports org.xbib.netty.http.common.util;
    requires transitive org.xbib.net.url;
    requires transitive io.netty.buffer;
    requires transitive io.netty.common;
    requires transitive io.netty.transport;
    requires transitive io.netty.handler;
    requires transitive io.netty.codec;
    requires transitive io.netty.codec.http;
    requires transitive io.netty.codec.http2;
    requires java.logging;
}
