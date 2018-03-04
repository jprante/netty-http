package org.xbib.netty.http.client.test.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.xbib.netty.http.client.HttpAddress;
import org.xbib.netty.http.client.pool.Pool;
import org.xbib.netty.http.client.pool.SimpleChannelPool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EpollTest {

    private static final Logger logger = Logger.getLogger(EpollTest.class.getName());

    private static final int CONCURRENCY = 10;

    private static final List<HttpAddress> NODES =
            Collections.singletonList(HttpAddress.http1("localhost", 12345));

    private static final long TEST_TIME_SECONDS = 100;

    private static final int ATTEMPTS = 10_000;

    private static final int FAIL_EVERY_ATTEMPT = 10;

    private static final ByteBuf PAYLOAD = Unpooled.directBuffer(0x1000).writeZero(0x1000);

    private MockEpollServer mockEpollServer;

    private Pool<Channel> channelPool;

    private EventLoopGroup eventLoopGroup;

    @Before
    public void setUp() throws Exception {
        mockEpollServer = new MockEpollServer(12345, FAIL_EVERY_ATTEMPT);
        Semaphore semaphore = new Semaphore(CONCURRENCY);
        eventLoopGroup = new EpollEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap()
            .group(eventLoopGroup)
            .channel(EpollSocketChannel.class)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) {
                    socketChannel.pipeline().addLast(new DummyClientChannelHandler());
                }
            })
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.SO_REUSEADDR, true)
            .option(ChannelOption.TCP_NODELAY, true);
        channelPool = new SimpleChannelPool<>(semaphore, NODES, bootstrap, null, 0);
        channelPool.prepare(CONCURRENCY);
    }

    @After
    public void tearDown() throws Exception {
        channelPool.close();
        eventLoopGroup.shutdownGracefully();
        mockEpollServer.close();
    }

    @Ignore
    @Test
    public void testPoolEpoll() throws Exception {
        LongAdder longAdder = new LongAdder();
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        for(int i = 0; i < CONCURRENCY; i ++) {
            executor.submit(() -> {
                Channel channel;
                for(int j = 0; j < ATTEMPTS; j ++) {
                    try {
                        while ((channel = channelPool.acquire()) == null) {
                            Thread.sleep(1); // very short?
                        }
                        channel.writeAndFlush(PAYLOAD.retain()).sync();
                        channelPool.release(channel);
                        longAdder.increment();
                    } catch (InterruptedException e) {
                        break;
                    } catch (Throwable cause) {
                        logger.log(Level.WARNING, cause.getMessage(), cause);
                    }
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(TEST_TIME_SECONDS, TimeUnit.SECONDS);
        assertTrue(executor.isTerminated());
        assertEquals(CONCURRENCY * ATTEMPTS, longAdder.sum(),
                2 * CONCURRENCY * ATTEMPTS / FAIL_EVERY_ATTEMPT);
    }

    class DummyClientChannelHandler extends SimpleChannelInboundHandler<Object> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            logger.log(Level.WARNING, cause.getMessage(), cause);
        }
    }

}
