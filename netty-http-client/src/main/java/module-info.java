import org.xbib.netty.http.client.Http1;
import org.xbib.netty.http.client.Http2;

module org.xbib.netty.http.client {
    uses org.xbib.netty.http.client.api.ClientProtocolProvider;
    uses org.xbib.netty.http.common.TransportProvider;
    exports org.xbib.netty.http.client;
    exports org.xbib.netty.http.client.cookie;
    exports org.xbib.netty.http.client.handler.http;
    exports org.xbib.netty.http.client.handler.http2;
    exports org.xbib.netty.http.client.pool;
    exports org.xbib.netty.http.client.retry;
    exports org.xbib.netty.http.client.transport;
    requires transitive org.xbib.netty.http.client.api;
    requires transitive org.xbib.netty.http.common;
    requires org.xbib.net;
    requires io.netty.handler.proxy;
    requires java.logging;
    requires io.netty.transport;
    requires io.netty.buffer;
    requires io.netty.codec.http;
    requires io.netty.codec.http2;
    requires io.netty.handler;
    requires io.netty.common;
    provides org.xbib.netty.http.client.api.ClientProtocolProvider with Http1, Http2;
}
