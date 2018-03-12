package org.xbib.netty.http.client.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;
import org.xbib.netty.http.common.PoolKey;

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

    private final boolean isSecure;

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

    /**
     * @param semaphore the concurrency level
     * @param httpVersion  the HTTP version of the pool connections
     * @param isSecure if this pool has secure connections
     * @param nodes the endpoint nodes, any element may contain the port (followed after ":")
     *             to override the defaultPort argument
     * @param bootstrap bootstrap instance
     * @param channelPoolHandler channel pool handler being notified upon new connection is created
     * @param retriesPerNode the max count of the subsequent connection failures to the node before
     *                       the node will be excluded from the pool. If set to 0, the value is ignored.
     */
    public BoundedChannelPool(Semaphore semaphore, HttpVersion httpVersion, boolean isSecure,
                              List<K> nodes, Bootstrap bootstrap,
                              ChannelPoolHandler channelPoolHandler, int retriesPerNode) {
        this.semaphore = semaphore;
        this.httpVersion = httpVersion;
        this.isSecure = isSecure;
        this.channelPoolhandler = channelPoolHandler;
        this.nodes = nodes;
        this.retriesPerNode = retriesPerNode;
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
    }

    public HttpVersion getVersion() {
        return httpVersion;
    }

    public boolean isSecure() {
        return isSecure;
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
                throw new ConnectException("failed to prepare");
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
        logger.log(Level.FINE,"prepared " + channelCount + " channels");
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
    public int acquire(List<Channel> channels, int maxCount) throws Exception {
        int availableCount = semaphore.drainPermits();
        if (availableCount == 0) {
            return availableCount;
        }
        if (availableCount > maxCount) {
            semaphore.release(availableCount - maxCount);
            availableCount = maxCount;
        }
        Channel channel;
        for (int i = 0; i < availableCount; i ++) {
            if ((channel = poll()) == null) {
                channel = newConnection();
            }
            if (channel == null) {
                semaphore.release(availableCount - i);
                throw new ConnectException();
            } else {
                if (channelPoolhandler != null) {
                    channelPoolhandler.channelAcquired(channel);
                }
                channels.add(channel);
            }
        }
        return availableCount;
    }

    @Override
    public void release(Channel channel) throws Exception {
        try {
            if (channel != null) {
                if (channel.isActive()) {
                    K key = channel.attr(attributeKey).get();
                    Queue<Channel> channelQueue = availableChannels.get(key);
                    if (channelQueue != null) {
                        channelQueue.add(channel);
                    }
                } else if (channel.isOpen()) {
                    channel.close();
                } else {
                    logger.log(Level.WARNING, "channel not active or open while release");
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
    public void release(List<Channel> channels) throws Exception {
        for (Channel channel : channels) {
            release(channel);
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
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
                    logger.log(Level.FINE, "closing channel " + channel);
                    channel.close();
                    count++;
                }
            }
            availableChannels.clear();
            channels.clear();
            bootstraps.clear();
            counts.clear();
            logger.log(Level.FINE, "closed " + count + " connections");
        } finally {
            lock.unlock();
        }
    }

    private Channel newConnection() throws ConnectException {
        Channel channel = null;
        K key = null;
        K nextKey;
        int min = Integer.MAX_VALUE;
        int next;
        int i = ThreadLocalRandom.current().nextInt(numberOfNodes);
        for (int j = i; j < numberOfNodes; j ++) {
            nextKey = nodes.get(j % numberOfNodes);
            if (counts == null) {
                throw new ConnectException("strange");
            }
            next = counts.get(nextKey);
            if (next == 0) {
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
            logger.log(Level.FINE,"new connection to " + key + " created");
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
        int i = ThreadLocalRandom.current().nextInt(numberOfNodes);
        Queue<Channel> channelQueue;
        Channel channel;
        for(int j = i; j < i + numberOfNodes; j ++) {
            K key = nodes.get(j % numberOfNodes);
            // for HTTP/2, use channel list
            logger.log(Level.FINE, "pool version = " + httpVersion);
            if (httpVersion.majorVersion() == 2) {
                List<Channel> list = channels.get(key);
                if (!list.isEmpty()) {
                    logger.log(Level.INFO, "we have a channel " + list);
                }
            }
            channelQueue = availableChannels.get(key);
            if (channelQueue != null) {
                channel = channelQueue.poll();
                if (channel != null && channel.isActive()) {
                    return channel;
                }
            } else {
                logger.log(Level.FINE, "channelqueue is null");
            }
        }
        return null;
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
