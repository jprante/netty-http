module org.xbib.netty.http.kqueue {
    exports org.xbib.netty.http.kqueue;
    requires org.xbib.netty.http.common;
    requires io.netty.transport;
    requires io.netty.transport.classes.kqueue;
    provides org.xbib.netty.http.common.TransportProvider with
            org.xbib.netty.http.kqueue.KqueueTransportProvider;
}
