module org.xbib.netty.http.common {
    exports org.xbib.netty.http.common;
    exports org.xbib.netty.http.common.cookie;
    exports org.xbib.netty.http.common.net;
    exports org.xbib.netty.http.common.mime;
    exports org.xbib.netty.http.common.security;
    exports org.xbib.netty.http.common.util;
    requires transitive org.xbib.net.url;
    requires io.netty.buffer;
    requires io.netty.common;
    requires io.netty.transport;
    requires io.netty.handler;
    requires io.netty.codec.http;
    requires java.logging;
}
