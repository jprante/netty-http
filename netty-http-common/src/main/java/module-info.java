module org.xbib.netty.http.common {
    exports org.xbib.netty.http.common;
    exports org.xbib.netty.http.common.cookie;
    exports org.xbib.netty.http.common.mime;
    exports org.xbib.netty.http.common.security;
    exports org.xbib.netty.http.common.util;
    exports org.xbib.netty.http.common.ws;
    requires org.xbib.net;
    requires io.netty.buffer;
    requires io.netty.common;
    requires io.netty.transport;
    requires io.netty.handler;
    requires io.netty.codec;
    requires io.netty.codec.http;
    requires io.netty.codec.http2;
    requires java.logging;
}
