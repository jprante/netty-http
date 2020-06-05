module org.xbib.netty.http.client.rest {
    exports org.xbib.netty.http.client.rest;
    requires transitive org.xbib.netty.http.client;
    requires org.xbib.net.url;
    requires io.netty.buffer;
    requires io.netty.codec.http;
}
