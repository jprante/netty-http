package org.xbib.netty.http.client.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.util.AttributeKey;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleChannelPool<K extends PoolKey> implements Pool<Channel> {

    private static final Logger logger = Logger.getLogger(SimpleChannelPool.class.getName());

    private final Semaphore semaphore;

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
     * @param nodes the endpoint nodes, any element may contain the port (followed after ":")
     *             to override the defaultPort argument
     * @param bootstrap bootstrap instance
     * @param channelPoolHandler channel pool handler being notified upon new connection is created
     * @param retriesPerNode the max count of the subsequent connection failures to the node before
     *                       the node will be excluded from the pool. If set to 0, the value is ignored.
     */
    public SimpleChannelPool(Semaphore semaphore, List<K> nodes, Bootstrap bootstrap,
                             ChannelPoolHandler channelPoolHandler, int retriesPerNode) {
        this.semaphore = semaphore;
        this.channelPoolhandler = channelPoolHandler;
        this.nodes = nodes;
        this.retriesPerNode = retriesPerNode;
        this.lock = new ReentrantLock();
        this.attributeKey = AttributeKey.valueOf("poolKey");
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("empty nodes array argument");
        }
        this.numberOfNodes = nodes.size();
        bootstraps = new HashMap<>(numberOfNodes);
        channels = new HashMap<>(numberOfNodes);
        availableChannels = new HashMap<>(numberOfNodes);
        counts = new HashMap<>(numberOfNodes);
        failedCounts = new HashMap<>(numberOfNodes);
        for (K node : nodes) {
            bootstraps.put(node, bootstrap.clone().remoteAddress(node.getInetSocketAddress())
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) throws Exception {
                        if(!channel.eventLoop().inEventLoop()) {
                            throw new IllegalStateException();
                        }
                        if (channelPoolHandler != null) {
                            channelPoolHandler.channelCreated(channel);
                        }
                    }
                }));
            availableChannels.put(node, new ConcurrentLinkedQueue<>());
            counts.put(node, 0);
            failedCounts.put(node, 0);
        }
    }

    @Override
    public void prepare(int count) throws ConnectException {
        if (count > 0) {
            for (int i = 0; i < count; i ++) {
                Channel channel = connectToAnyNode();
                if (channel == null) {
                    throw new ConnectException("failed to prepare the connections");
                }
                K nodeAddr = channel.attr(attributeKey).get();
                if (channel.isActive()) {
                    Queue<Channel> channelQueue = availableChannels.get(nodeAddr);
                    if (channelQueue != null) {
                        channelQueue.add(channel);
                    }
                } else {
                    channel.close();
                }
            }
            logger.log(Level.FINE,"prepared " + count + " connections");
        } else {
            throw new IllegalArgumentException("Connection count should be > 0, but got " + count);
        }
    }

    @Override
    public Channel acquire() throws Exception {
        Channel channel = null;
        if (semaphore.tryAcquire()) {
            if ((channel = poll()) == null) {
                channel = connectToAnyNode();
            }
            if (channel == null) {
                semaphore.release();
                throw new ConnectException();
            }
        }
        if (channelPoolhandler != null) {
            channelPoolhandler.channelAcquired(channel);
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
            if (null == (channel = poll())) {
                channel = connectToAnyNode();
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
        K nodeAddr = channel.attr(attributeKey).get();
        if (channel.isActive()) {
            Queue<Channel> channelQueue = availableChannels.get(nodeAddr);
            if (channelQueue != null) {
                channelQueue.add(channel);
            }
            semaphore.release();
        } else {
            channel.close();
        }
        if (channelPoolhandler != null) {
            channelPoolhandler.channelReleased(channel);
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
            int closedConnCount = 0;
            for (K nodeAddr : availableChannels.keySet()) {
                for (Channel conn : availableChannels.get(nodeAddr)) {
                    if (conn.isOpen()) {
                        conn.close();
                        closedConnCount++;
                    }
                }
            }
            availableChannels.clear();
            for (K nodeAddr : channels.keySet()) {
                for (Channel channel : channels.get(nodeAddr)) {
                    if (channel != null && channel.isOpen()) {
                        channel.close();
                        closedConnCount++;
                    }
                }
            }
            channels.clear();
            bootstraps.clear();
            counts.clear();
            logger.log(Level.FINE, "closed " + closedConnCount + " connections");
        } finally {
            lock.unlock();
        }
    }

    private Channel connectToAnyNode() throws ConnectException {
        Channel channel = null;
        K nodeAddr = null;
        K nextNodeAddr;
        int min = Integer.MAX_VALUE;
        int next;
        int i = ThreadLocalRandom.current().nextInt(numberOfNodes);
        for (int j = i; j < numberOfNodes; j ++) {
            nextNodeAddr = nodes.get(j % numberOfNodes);
            next = counts.get(nextNodeAddr);
            if(next == 0) {
                nodeAddr = nextNodeAddr;
                break;
            } else if (next < min) {
                min = next;
                nodeAddr = nextNodeAddr;
            }
        }
        if (nodeAddr != null) {
            logger.log(Level.FINE, "trying connection to " + nodeAddr);
            try {
                channel = connect(nodeAddr);
            } catch (Exception e) {
                logger.log(Level.WARNING, "failed to create a new connection to " + nodeAddr + ": " + e.toString());
                if (retriesPerNode > 0) {
                    int selectedNodeFailedConnAttemptsCount = failedCounts.get(nodeAddr) + 1;
                    failedCounts.put(nodeAddr, selectedNodeFailedConnAttemptsCount);
                    if (selectedNodeFailedConnAttemptsCount > retriesPerNode) {
                        logger.log(Level.WARNING, "failed to connect to the node " + nodeAddr + " "
                                        + selectedNodeFailedConnAttemptsCount + " times, "
                                        + "excluding the node from the connection pool");
                        counts.put(nodeAddr, Integer.MAX_VALUE);
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
            channel.closeFuture().addListener(new CloseChannelListener(nodeAddr, channel));
            channel.attr(attributeKey).set(nodeAddr);
            channels.computeIfAbsent(nodeAddr, node -> new ArrayList<>()).add(channel);
            synchronized (counts) {
                counts.put(nodeAddr, counts.get(nodeAddr) + 1);
            }
            if(retriesPerNode > 0) {
                failedCounts.put(nodeAddr, 0);
            }
            logger.log(Level.FINE,"new connection to " + nodeAddr + " created");
        }
        return channel;
    }

    private Channel connect(K addr) throws Exception {
        Bootstrap bootstrap = bootstraps.get(addr);
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
            channelQueue = availableChannels.get(nodes.get(j % numberOfNodes));
            if (channelQueue != null) {
                channel = channelQueue.poll();
                if (channel != null && channel.isActive()) {
                    return channel;
                }
            }
        }
        return null;
    }

    private class CloseChannelListener implements ChannelFutureListener {

        private final K nodeAddr;
        private final Channel channel;

        private CloseChannelListener(K nodeAddr, Channel channel) {
            this.nodeAddr = nodeAddr;
            this.channel = channel;
        }

        @Override
        public void operationComplete(ChannelFuture future) {
            logger.log(Level.FINE,"connection to " + nodeAddr + " closed");
            lock.lock();
            try {
                synchronized (counts) {
                    if (counts.containsKey(nodeAddr)) {
                        counts.put(nodeAddr, counts.get(nodeAddr) - 1);
                    }
                }
                synchronized (channels) {
                    List<Channel> channels = SimpleChannelPool.this.channels.get(nodeAddr);
                    if (channels != null) {
                        channels.remove(channel);
                    }
                }
                semaphore.release();
            } finally {
                lock.unlock();
            }
        }
    }
}
