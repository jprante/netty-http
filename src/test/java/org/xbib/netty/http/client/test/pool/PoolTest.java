package org.xbib.netty.http.client.test.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xbib.netty.http.client.HttpAddress;
import org.xbib.netty.http.client.pool.BoundedChannelPool;
import org.xbib.netty.http.client.pool.Pool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class PoolTest {

    private static final Logger logger = Logger.getLogger(PoolTest.class.getName());

    private static final int TEST_STEP_TIME_SECONDS = 50;

    private static final int BATCH_SIZE = 0x1000;

    private int nodeCount;

    private ConcurrentMap<HttpAddress, LongAdder> nodeFreq = new ConcurrentHashMap<>();

    @Parameterized.Parameters
    public static Collection<Object[]> generateData() {
        return Arrays.asList(new Object[][] {
                        {1, 1},
                        {10, 1}, {10, 2}, {10, 5}, {10, 10},
                        {100, 1}, {100, 2}, {100, 5}, {100, 10},
                        {1000, 1}, {1000, 2}, {1000, 5}, {1000, 10}
                });
    }

    public PoolTest(int concurrencyLevel, int nodeCount) {
        this.nodeCount = nodeCount;
        List<HttpAddress> nodes = new ArrayList<>();
        for (int i = 0; i < nodeCount; i ++) {
            nodes.add(HttpAddress.http1("localhost" + i));
        }
        try (Pool<Channel> pool = new BoundedChannelPool<>(new Semaphore(concurrencyLevel), HttpVersion.HTTP_1_1, false,
                nodes, new Bootstrap(), null, 0)) {
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
                                pool.release(channels.get(k));
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
            long connCountSum = nodeFreq.values().stream().mapToLong(LongAdder::sum).sum();
            logger.log(Level.INFO, "concurrency = " + concurrencyLevel + ", nodes = " + nodeCount + " -> rate: " +
                            connCountSum / TEST_STEP_TIME_SECONDS);
        }
    }

    @Test
    public void testNodeFrequency() {
        if (nodeCount > 1) {
            long connCountSum = nodeFreq.values().stream().mapToLong(LongAdder::sum).sum();
            long avgConnCountPerNode = connCountSum / nodeCount;
            for (HttpAddress nodeAddr: nodeFreq.keySet()) {
                assertTrue(nodeFreq.get(nodeAddr).sum() > 0);
                assertEquals("Node count: " + nodeCount + ", node: " + nodeAddr
                                + ", expected connection count: " + avgConnCountPerNode + ", actual: "
                                + nodeFreq.get(nodeAddr).sum(),
                        avgConnCountPerNode, nodeFreq.get(nodeAddr).sum(), 1.5 * avgConnCountPerNode);
            }
        } else {
            assertTrue(true);
        }
    }
}
