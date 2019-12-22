package org.xbib.netty.http.common;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import java.util.concurrent.ThreadFactory;

public interface TransportProvider {

    EventLoopGroup createEventLoopGroup(int nThreads, ThreadFactory threadFactory);

    Class<? extends SocketChannel> createSocketChannelClass();

    Class<? extends ServerSocketChannel> createServerSocketChannelClass();

}
