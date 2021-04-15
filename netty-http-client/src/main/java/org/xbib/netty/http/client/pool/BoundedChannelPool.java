package org.xbib.netty.http.client.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.util.AttributeKey;
import org.xbib.netty.http.client.api.Pool;
import org.xbib.netty.http.common.PoolKey;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BoundedChannelPool<K extends PoolKey> implements Pool<Channel> {

    private static final Logger logger = Logger.getLogger(BoundedChannelPool.class.getName());

    private final Semaphore semaphore;

    private final HttpVersion httpVersion;

    private final ChannelPoolHandler channelPoolhandler;

    private final List<K> nodes;

    private final int numberOfNodes;

    private final int retriesPerNode;

    private final Map<K, Bootstrap> bootstraps;

    private final Map<K, List<Channel>> channels;

    private final Map<K, Queue<Channel>> availableChannels;

    private final Map<K, Integer> counts;

    private final Map<K, Integer> failedCounts;

    private final Lock lock;

    private final AttributeKey<K> attributeKey;

    private PoolKeySelector<K> poolKeySelector;

    /**
     * A bounded channel pool.
     *
     * @param semaphore the level of concurrency
     * @param httpVersion the HTTP version of the pool connections
     * @param nodes the endpoint nodes, any element may contain the port (followed after ":")
     *             to override the defaultPort argument
     * @param bootstrap bootstrap instance
     * @param channelPoolHandler channel pool handler being notified upon new connection is created
     * @param retriesPerNode the max count of the subsequent connection failures to the node before
     *                       the node will be excluded from the pool. If set to 0, the value is ignored.
     * @param poolKeySelectorType pool key selector type
     */
    public BoundedChannelPool(Semaphore semaphore, HttpVersion httpVersion,
                              List<K> nodes, Bootstrap bootstrap,
                              ChannelPoolHandler channelPoolHandler, int retriesPerNode,
                              PoolKeySelectorType poolKeySelectorType) {
        this.semaphore = semaphore;
        this.httpVersion = httpVersion;
        this.channelPoolhandler = channelPoolHandler;
        this.nodes = nodes;
        this.retriesPerNode = retriesPerNode;
        switch (poolKeySelectorType) {
            case RANDOM:
                this.poolKeySelector = new RandomPoolKeySelector();
                break;
            case ROUNDROBIN:
                this.poolKeySelector = new RoundRobinKeySelector();
                break;
        }
        this.lock = new ReentrantLock();
        this.attributeKey = AttributeKey.valueOf("poolKey");
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("nodes must not be empty");
        }
        this.numberOfNodes = nodes.size();
        bootstraps = new HashMap<>(numberOfNodes);
        channels = new ConcurrentHashMap<>(numberOfNodes);
        availableChannels = new ConcurrentHashMap<>(numberOfNodes);
        counts = new ConcurrentHashMap<>(numberOfNodes);
        failedCounts = new ConcurrentHashMap<>(numberOfNodes);
        for (K node : nodes) {
            ChannelPoolInitializer initializer = new ChannelPoolInitializer(node, channelPoolHandler);
            bootstraps.put(node, bootstrap.clone().remoteAddress(node.getInetSocketAddress())
                .handler(initializer));
            availableChannels.put(node, new ConcurrentLinkedQueue<>());
            counts.put(node, 0);
            failedCounts.put(node, 0);
        }
        logger.log(Level.FINE, "pool is up");
    }

    public HttpVersion getVersion() {
        return httpVersion;
    }

    public AttributeKey<K> getAttributeKey() {
        return attributeKey;
    }

    @Override
    public void prepare(int channelCount) throws ConnectException {
        if (channelCount <= 0) {
            throw new IllegalArgumentException("channel count must be greater zero, but got " + channelCount);
        }
        for (int i = 0; i < channelCount; i++) {
            Channel channel = newConnection();
            if (channel == null) {
                throw new ConnectException("failed to prepare channels");
            }
            K key = channel.attr(attributeKey).get();
            if (channel.isActive()) {
                Queue<Channel> channelQueue = availableChannels.get(key);
                if (channelQueue != null) {
                    channelQueue.add(channel);
                }
            } else {
                channel.close();
            }
        }
        logger.log(Level.FINE,"prepared " + channelCount + " channels: " + availableChannels);
    }

    @Override
    public Channel acquire() throws Exception {
        Channel channel = null;
        if (semaphore.tryAcquire()) {
            if ((channel = poll()) == null) {
                channel = newConnection();
            }
            if (channel == null) {
                semaphore.release();
                throw new ConnectException();
            } else {
                if (channelPoolhandler != null) {
                    channelPoolhandler.channelAcquired(channel);
                }
            }
        }
        return channel;
    }

    @Override
    public void release(Channel channel, boolean close) throws Exception {
        try {
            if (channel != null) {
                if (channel.isActive()) {
                    K key = channel.attr(attributeKey).get();
                    if (key != null) {
                        Queue<Channel> channelQueue = availableChannels.get(key);
                        if (channelQueue != null) {
                            channelQueue.add(channel);
                        }
                    }
                } else if (channel.isOpen() && close) {
                    logger.log(Level.FINE, "closing channel " + channel);
                    channel.close();
                }
                if (channelPoolhandler != null) {
                    channelPoolhandler.channelReleased(channel);
                }
            }
        } finally {
            semaphore.release();
        }
    }

    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            logger.log(Level.FINE, "closing pool");
            int count = 0;
            Set<Channel> channelSet = new HashSet<>();
            for (Map.Entry<K, Queue<Channel>> entry : availableChannels.entrySet()) {
                channelSet.addAll(entry.getValue());
            }
            for (Map.Entry<K, List<Channel>> entry : channels.entrySet()) {
                channelSet.addAll(entry.getValue());
            }
            for (Channel channel : channelSet) {
                if (channel != null && channel.isOpen()) {
                    logger.log(Level.FINE, "trying to abort channel " + channel);
                    if (httpVersion.majorVersion() == 2) {
                        // be polite, send a go away frame
                        DefaultHttp2GoAwayFrame goAwayFrame = new DefaultHttp2GoAwayFrame(0);
                        ChannelPromise channelPromise = channel.newPromise();
                        channel.writeAndFlush(goAwayFrame, channelPromise);
                        try {
                            channelPromise.get();
                            logger.log(Level.FINE, "goaway frame sent to " + channel);
                        } catch (ExecutionException e) {
                            logger.log(Level.FINE, e.getMessage(), e);
                        } catch (InterruptedException e) {
                            throw new IOException(e);
                        }
                    }
                    channel.close();
                    count++;
                }
            }
            availableChannels.clear();
            channels.clear();
            bootstraps.clear();
            counts.clear();
            logger.log(Level.FINE, "closed pool (found " + count + " connections open)");
        } finally {
            lock.unlock();
        }
    }

    private Channel newConnection() throws ConnectException {
        Channel channel = null;
        K key = null;
        Integer min = Integer.MAX_VALUE;
        Integer next;
        for (int j = 0; j < numberOfNodes; j++) {
            K nextKey = poolKeySelector.key();
            next = counts.get(nextKey);
            if (next == null || next == 0) {
                key = nextKey;
                break;
            } else if (next < min) {
                min = next;
                key = nextKey;
            }
        }
        if (key != null) {
            logger.log(Level.FINE, "trying connection to " + key);
            try {
                channel = connect(key);
            } catch (Exception e) {
                logger.log(Level.WARNING, "failed to create a new connection to " + key + ": " + e.toString());
                if (retriesPerNode > 0) {
                    int selectedNodeFailedConnAttemptsCount = failedCounts.get(key) + 1;
                    failedCounts.put(key, selectedNodeFailedConnAttemptsCount);
                    if (selectedNodeFailedConnAttemptsCount > retriesPerNode) {
                        logger.log(Level.WARNING, "failed to connect to the node " + key + " "
                                        + selectedNodeFailedConnAttemptsCount + " times, "
                                        + "excluding the node from the connection pool");
                        counts.put(key, Integer.MAX_VALUE);
                        boolean allNodesExcluded = true;
                        for (K node : nodes) {
                            if (counts.get(node) < Integer.MAX_VALUE) {
                                allNodesExcluded = false;
                                break;
                            }
                        }
                        if (allNodesExcluded) {
                            logger.log(Level.SEVERE, "no nodes left in the connection pool");
                        }
                    }
                }
                if (e instanceof ConnectException) {
                    throw (ConnectException) e;
                } else {
                    throw new ConnectException(e.getMessage());
                }
            }
        }
        if (channel != null) {
            channel.closeFuture().addListener(new CloseChannelListener(key, channel));
            channel.attr(attributeKey).set(key);
            channels.computeIfAbsent(key, node -> new ArrayList<>()).add(channel);
            counts.put(key, counts.get(key) + 1);
            if (retriesPerNode > 0) {
                failedCounts.put(key, 0);
            }
        }
        return channel;
    }

    private Channel connect(K key) throws Exception {
        Bootstrap bootstrap = bootstraps.get(key);
        if (bootstrap != null) {
            return bootstrap.connect().sync().channel();
        }
        return null;
    }

    private Channel poll() {
        Queue<Channel> channelQueue;
        Channel channel;
        for (int j = 0; j < numberOfNodes; j++) {
            K key = poolKeySelector.key();
            logger.log(Level.FINE, "poll: key = " + key);
            channelQueue = availableChannels.get(key);
            if (channelQueue != null) {
                channel = channelQueue.poll();
                if (channel != null && channel.isActive()) {
                    return channel;
                }
            } else {
                logger.log(Level.WARNING, "channel queue is null?");
            }
        }
        return null;
    }

    private interface PoolKeySelector<K extends PoolKey> {
        K key();
    }

    private class RandomPoolKeySelector implements PoolKeySelector<K> {

        @Override
        public K key() {
            int r = ThreadLocalRandom.current().nextInt(numberOfNodes);
            return nodes.get(r % numberOfNodes);
        }
    }

    private class RoundRobinKeySelector implements PoolKeySelector<K> {

        int r = 0;

        @Override
        public K key() {
            return nodes.get(r++ % numberOfNodes);
        }
    }

    private class CloseChannelListener implements ChannelFutureListener {

        private final K key;
        private final Channel channel;

        private CloseChannelListener(K key, Channel channel) {
            this.key = key;
            this.channel = channel;
        }

        @Override
        public void operationComplete(ChannelFuture future) {
            logger.log(Level.FINE,"connection to " + key + " closed");
            lock.lock();
            try {
                if (counts.containsKey(key)) {
                    counts.put(key, counts.get(key) - 1);
                }
                List<Channel> channels = BoundedChannelPool.this.channels.get(key);
                if (channels != null) {
                    channels.remove(channel);
                }
                semaphore.release();
            } finally {
                lock.unlock();
            }
        }
    }

    class ChannelPoolInitializer extends ChannelInitializer<SocketChannel> {

        private final K key;

        private final ChannelPoolHandler channelPoolHandler;

        ChannelPoolInitializer(K key, ChannelPoolHandler channelPoolHandler) {
            this.key = key;
            this.channelPoolHandler = channelPoolHandler;
        }

        @Override
        protected void initChannel(SocketChannel channel) throws Exception {
            if (!channel.eventLoop().inEventLoop()) {
                throw new IllegalStateException();
            }
            channel.attr(attributeKey).set(key);
            if (channelPoolHandler != null) {
                channelPoolHandler.channelCreated(channel);
            }
        }
    }
}
