package org.xbib.netty.http.kqueue;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import org.xbib.netty.http.common.TransportProvider;
import java.util.concurrent.ThreadFactory;

public class KqueueTransportProvider implements TransportProvider {

    @Override
    public EventLoopGroup createEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        return KQueue.isAvailable() ? new KQueueEventLoopGroup(nThreads, threadFactory) : null;
    }

    @Override
    public Class<? extends SocketChannel> createSocketChannelClass() {
        return KQueue.isAvailable() ? KQueueSocketChannel.class : null;
    }

    @Override
    public Class<? extends ServerSocketChannel> createServerSocketChannelClass() {
        return KQueue.isAvailable() ? KQueueServerSocketChannel.class : null;
    }
}
