package org.xbib.netty.http.epoll;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import org.xbib.netty.http.common.TransportProvider;
import java.util.concurrent.ThreadFactory;

public class EpollTransportProvider implements TransportProvider {

    @Override
    public EventLoopGroup createEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        return Epoll.isAvailable() ? new EpollEventLoopGroup(nThreads, threadFactory) : null;
    }

    @Override
    public Class<? extends SocketChannel> createSocketChannelClass() {
        return Epoll.isAvailable() ? EpollSocketChannel.class : null;
    }

    @Override
    public Class<? extends ServerSocketChannel> createServerSocketChannelClass() {
        return Epoll.isAvailable() ? EpollServerSocketChannel.class : null;
    }
}
