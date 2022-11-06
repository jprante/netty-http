import org.xbib.netty.http.server.api.ServerCertificateProvider;
import org.xbib.netty.http.server.protocol.http1.Http1;
import org.xbib.netty.http.server.protocol.http2.Http2;

module org.xbib.netty.http.server {
    uses ServerCertificateProvider;
    uses org.xbib.netty.http.server.api.ServerProtocolProvider;
    uses org.xbib.netty.http.common.TransportProvider;
    exports org.xbib.netty.http.server;
    exports org.xbib.netty.http.server.cookie;
    exports org.xbib.netty.http.server.endpoint;
    exports org.xbib.netty.http.server.endpoint.service;
    exports org.xbib.netty.http.server.handler;
    exports org.xbib.netty.http.server.protocol.http1;
    exports org.xbib.netty.http.server.protocol.http2;
    exports org.xbib.netty.http.server.util;
    requires transitive org.xbib.netty.http.server.api;
    requires transitive org.xbib.netty.http.common;
    requires org.xbib.net;
    requires org.xbib.net.path;
    requires java.logging;
    requires io.netty.buffer;
    requires io.netty.common;
    requires io.netty.handler;
    requires io.netty.transport;
    requires io.netty.codec.http;
    requires io.netty.codec.http2;
    provides org.xbib.netty.http.server.api.ServerProtocolProvider with Http1, Http2;
}
