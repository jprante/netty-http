package org.xbib.netty.http.client.test.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.client.pool.BoundedChannelPool;
import org.xbib.netty.http.client.api.Pool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoolTest {

    private static final Logger logger = Logger.getLogger(PoolTest.class.getName());

    private static final long TEST_STEP_TIME_SECONDS = 60L;

    private static final int BATCH_SIZE = 100;

    @ParameterizedTest
    @ValueSource(ints = {1,10,25})
    void testPool(int concurrencyLevel) throws InterruptedException {
        ConcurrentMap<HttpAddress, LongAdder> nodeFreq = new ConcurrentHashMap<>();
        int nodecount = 2;

        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(new NioEventLoopGroup())
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                    }
                });
        Channel serverChannel = serverBootstrap.bind("localhost", 8008).sync().channel();

        List<HttpAddress> nodes = new ArrayList<>();
        for (int i = 0; i < nodecount; i ++) {
            nodes.add(HttpAddress.http1("localhost", 8008));
        }
        try (Pool<Channel> pool = new BoundedChannelPool<>(new Semaphore(concurrencyLevel), HttpVersion.HTTP_1_1,
                nodes, new Bootstrap().group(new NioEventLoopGroup()).channel(NioSocketChannel.class),
                null, 0, BoundedChannelPool.PoolKeySelectorType.ROUNDROBIN)) {
            int n = Runtime.getRuntime().availableProcessors();
            ExecutorService executorService = Executors.newFixedThreadPool(n);
            for(int i = 0; i < n; i ++) {
                executorService.submit(() -> {
                    Thread currThread = Thread.currentThread();
                    List<Channel> channels = new ArrayList<>(BATCH_SIZE);
                    int j;
                    int k;
                    Channel channel;
                    try {
                        while (!currThread.isInterrupted()) {
                            for (j = 0; j < BATCH_SIZE; j ++) {
                                channel = pool.acquire();
                                if (channel == null) {
                                    break;
                                }
                                AttributeKey<HttpAddress> attributeKey = AttributeKey.valueOf("poolKey");
                                nodeFreq.computeIfAbsent(channel.attr(attributeKey).get(), node -> new LongAdder()).increment();
                                channels.add(channel);
                            }
                            for (k = 0; k < j; k ++) {
                                pool.release(channels.get(k), false);
                            }
                            channels.clear();
                        }
                    } catch (Exception ignored) {
                        //
                    }
                });
            }
            executorService.shutdown();
            try {
                executorService.awaitTermination(TEST_STEP_TIME_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
            executorService.shutdownNow();
        } catch (Throwable t) {
            logger.log(Level.WARNING, t.getMessage(), t);
        } finally {
            serverChannel.close();
            long connCountSum = nodeFreq.values().stream().mapToLong(LongAdder::sum).sum();
            logger.log(Level.INFO, "concurrency = " + concurrencyLevel + ", nodes = " + nodecount + " -> rate: " +
                            connCountSum / TEST_STEP_TIME_SECONDS);
            long avgConnCountPerNode = connCountSum / 2;
            for (HttpAddress nodeAddr: nodeFreq.keySet()) {
                assertTrue(nodeFreq.get(nodeAddr).sum() > 0);
                assertEquals(avgConnCountPerNode, nodeFreq.get(nodeAddr).sum(), 1.5 * avgConnCountPerNode);
            }
        }
    }
}
