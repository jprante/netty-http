import org.xbib.netty.http.server.protocol.http1.Http1;
import org.xbib.netty.http.server.protocol.http2.Http2;

module org.xbib.netty.http.server {
    uses org.xbib.netty.http.server.api.security.ServerCertificateProvider;
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
    requires java.logging;
    provides org.xbib.netty.http.server.api.ServerProtocolProvider with Http1, Http2;
}
