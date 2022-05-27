module org.xbib.netty.http.epoll {
    exports org.xbib.netty.http.epoll;
    requires org.xbib.netty.http.common;
    requires io.netty.transport;
    requires io.netty.transport.classes.epoll;
    provides org.xbib.netty.http.common.TransportProvider with
            org.xbib.netty.http.epoll.EpollTransportProvider;
}
