module org.xbib.netty.http.client.rest {
    exports org.xbib.netty.http.client.rest;
    requires org.xbib.netty.http.client.api;
    requires org.xbib.netty.http.client;
    requires org.xbib.netty.http.common;
    requires org.xbib.net.url;
    requires io.netty.buffer;
    requires io.netty.codec.http;
}
