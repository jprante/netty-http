module org.xbib.netty.http.common {
    exports org.xbib.netty.http.common;
    exports org.xbib.netty.http.common.cookie;
    exports org.xbib.netty.http.common.net;
    exports org.xbib.netty.http.common.mime;
    exports org.xbib.netty.http.common.security;
    exports org.xbib.netty.http.common.util;
    requires io.netty.transport;
    requires io.netty.handler;
    requires org.xbib.net.url;
    requires java.logging;
}
