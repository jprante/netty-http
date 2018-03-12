package org.xbib.netty.http.client.test.pool;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.SocketChannel;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MockEpollServer implements Closeable {

    private static final Logger logger = Logger.getLogger(MockEpollServer.class.getName());

    private final EventLoopGroup dispatchGroup;

    private final EventLoopGroup workerGroup;

    private final ChannelFuture bindFuture;

    private final AtomicLong reqCounter;

    public MockEpollServer(int port, int dropEveryRequest) throws InterruptedException {
        dispatchGroup = new EpollEventLoopGroup();
        workerGroup = new EpollEventLoopGroup();
        reqCounter = new AtomicLong(0);
        ServerBootstrap bootstrap = new ServerBootstrap()
            .group(dispatchGroup, workerGroup)
            .channel(EpollServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) {
                    if (dropEveryRequest > 0) {
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                                if (reqCounter.incrementAndGet() % dropEveryRequest == 0) {
                                    Channel channel = ctx.channel();
                                    logger.log(Level.INFO,"dropping the connection " + channel);
                                    channel.close();
                                }
                            }
                        });
                    }
                }
            });
        bindFuture = bootstrap.bind(port).sync();
    }

    @Override
    public void close() {
        bindFuture.channel().close();
        workerGroup.shutdownGracefully();
        dispatchGroup.shutdownGracefully();
    }
}
