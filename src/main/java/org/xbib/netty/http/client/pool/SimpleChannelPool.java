package org.xbib.netty.http.client.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.pool.ChannelPoolHandler;

import java.net.ConnectException;
import java.net.InetSocketAddress;
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
import java.util.logging.Logger;


public class SimpleChannelPool implements ChannelPool {

    private static final Logger logger = Logger.getLogger(SimpleChannelPool.class.getName());

    private final Semaphore semaphore;

    private final List<String> nodes;

    private final int numberOfNodes;

    private final int retriesPerNode;

    private final Map<String, Bootstrap> bootstraps;

    private final Map<String, List<Channel>> channels;

    private final Map<String, Queue<Channel>> availableChannels;

    private final Map<String, Integer> counts;

    private final Map<String, Integer> failedCounts;

    private final Lock lock = new ReentrantLock();

    /**
     * @param semaphore the throttle for the concurrency level control
     * @param nodes the endpoint nodes, any element may contain the port (followed after ":")
     *             to override the defaultPort argument
     * @param bootstrap bootstrap instance
     * @param channelPoolHandler channel pool handler being notified upon new connection is created
     * @param defaultPort default port used to connect (any node address from the nodes set may override this)
     * @param retriesPerNode the max count of the subsequent connection failures to the node before
     *                       the node will be excluded from the pool, 0 means no limit
     */
    public SimpleChannelPool(Semaphore semaphore, List<String> nodes, Bootstrap bootstrap,
                             ChannelPoolHandler channelPoolHandler, int defaultPort, int retriesPerNode) {
        this.semaphore = semaphore;
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("empty nodes array argument");
        }
        this.nodes = nodes;
        this.retriesPerNode = retriesPerNode;
        this.numberOfNodes = nodes.size();
        bootstraps = new HashMap<>(numberOfNodes);
        channels = new HashMap<>(numberOfNodes);
        availableChannels = new HashMap<>(numberOfNodes);
        counts = new HashMap<>(numberOfNodes);
        failedCounts = new HashMap<>(numberOfNodes);
        for (String node : nodes) {
            InetSocketAddress nodeAddr;
            if (node.contains(":")) {
                String addrParts[] = node.split(":");
                nodeAddr = new InetSocketAddress(addrParts[0], Integer.parseInt(addrParts[1]));
            } else {
                nodeAddr = new InetSocketAddress(node, defaultPort);
            }
            bootstraps.put(node, bootstrap.clone().remoteAddress(nodeAddr)
                    .handler(new ChannelInitializer<Channel>() {
                                        @Override
                                        protected void initChannel(Channel conn) throws Exception {
                                            if(!conn.eventLoop().inEventLoop()) {
                                                throw new AssertionError();
                                            }
                                            channelPoolHandler.channelCreated(conn);
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
                if(channel == null) {
                    throw new ConnectException("Failed to pre-create the connections to the target nodes");
                }
                String nodeAddr = channel.attr(NODE_ATTRIBUTE_KEY).get();
                if (channel.isActive()) {
                    Queue<Channel> channelQueue = availableChannels.get(nodeAddr);
                    if (channelQueue != null) {
                        channelQueue.add(channel);
                    }
                } else {
                    channel.close();
                }
            }
            logger.info("prepared " + count + " connections");
        } else {
            throw new IllegalArgumentException("Connection count should be > 0, but got " + count);
        }
    }

    private class CloseChannelListener implements ChannelFutureListener {

        private final String nodeAddr;
        private final Channel conn;

        private CloseChannelListener(String nodeAddr, Channel conn) {
            this.nodeAddr = nodeAddr;
            this.conn = conn;
        }

        @Override
        public void operationComplete(ChannelFuture future) {
            logger.fine("connection to " + nodeAddr + " closed");
            lock.lock();
            try {
                synchronized (counts) {
                    if(counts.containsKey(nodeAddr)) {
                        counts.put(nodeAddr, counts.get(nodeAddr) - 1);
                    }
                }
                synchronized (channels) {
                    List<Channel> nodeConns = channels.get(nodeAddr);
                    if(nodeConns != null) {
                        nodeConns.remove(conn);
                    }
                }
                semaphore.release();
            } finally {
                lock.unlock();
            }
        }
    }

    private Channel connectToAnyNode() throws ConnectException {
        Channel channel = null;
        String nodeAddr = null;
        String nextNodeAddr;
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
            logger.fine("trying connection to " + nodeAddr);
            try {
                channel = connect(nodeAddr);
            } catch (Exception e) {
                logger.warning("failed to create a new connection to " + nodeAddr + ": " + e.toString());
                if (retriesPerNode > 0) {
                    int selectedNodeFailedConnAttemptsCount = failedCounts.get(nodeAddr) + 1;
                    failedCounts.put(nodeAddr, selectedNodeFailedConnAttemptsCount);
                    if (selectedNodeFailedConnAttemptsCount > retriesPerNode) {
                        logger.warning("Failed to connect to the node \"" + nodeAddr + "\" "
                                        + selectedNodeFailedConnAttemptsCount + " times successively, "
                                        + "excluding the node from the connection pool forever");
                        counts.put(nodeAddr, Integer.MAX_VALUE);
                        boolean allNodesExcluded = true;
                        for (String node : nodes) {
                            if (counts.get(node) < Integer.MAX_VALUE) {
                                allNodesExcluded = false;
                                break;
                            }
                        }
                        if (allNodesExcluded) {
                            logger.severe("no endpoint nodes left in the connection pool");
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
            channel.attr(NODE_ATTRIBUTE_KEY).set(nodeAddr);
            channels.computeIfAbsent(nodeAddr, na -> new ArrayList<>()).add(channel);
            synchronized(counts) {
                counts.put(nodeAddr, counts.get(nodeAddr) + 1);
            }
            if(retriesPerNode > 0) {
                failedCounts.put(nodeAddr, 0);
            }
            logger.fine("new connection to " + nodeAddr + " created");
        }
        return channel;
    }

    protected Channel connect(String addr) throws Exception {
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
            if(channelQueue != null) {
                channel = channelQueue.poll();
                if(channel != null && channel.isActive()) {
                    return channel;
                }
            }
        }
        return null;
    }

    @Override
    public Channel lease() throws ConnectException {
        Channel conn = null;
        if (semaphore.tryAcquire()) {
            if (null == (conn = poll())) {
                conn = connectToAnyNode();
            }
            if (conn == null) {
                semaphore.release();
                throw new ConnectException();
            }
        }
        return conn;
    }

    @Override
    public int lease(List<Channel> channels, int maxCount) throws ConnectException {
        int availableCount = semaphore.drainPermits();
        if (availableCount == 0) {
            return availableCount;
        }
        if (availableCount > maxCount) {
            semaphore.release(availableCount - maxCount);
            availableCount = maxCount;
        }
        Channel conn;
        for (int i = 0; i < availableCount; i ++) {
            if (null == (conn = poll())) {
                conn = connectToAnyNode();
            }
            if (conn == null) {
                semaphore.release(availableCount - i);
                throw new ConnectException();
            } else {
                channels.add(conn);
            }
        }
        return availableCount;
    }

    @Override
    public void release(Channel conn) {
        String nodeAddr = conn.attr(NODE_ATTRIBUTE_KEY).get();
        if( conn.isActive()) {
            Queue<Channel> connQueue = availableChannels.get(nodeAddr);
            if (connQueue != null) {
                connQueue.add(conn);
            }
            semaphore.release();
        } else {
            conn.close();
        }
    }

    @Override
    public void release(List<Channel> conns) {
        String nodeAddr;
        Queue<Channel> connQueue;
        for (Channel conn : conns) {
            nodeAddr = conn.attr(NODE_ATTRIBUTE_KEY).get();
            if (conn.isActive()) {
                connQueue = availableChannels.get(nodeAddr);
                connQueue.add(conn);
                semaphore.release();
            } else {
                conn.close();
            }
        }
    }

    @Override
    public void close() {
        lock.lock();
        int closedConnCount = 0;
        for (String nodeAddr: availableChannels.keySet()) {
            for (Channel conn: availableChannels.get(nodeAddr)) {
                if (conn.isOpen()) {
                    conn.close();
                    closedConnCount ++;
                }
            }
        }
        availableChannels.clear();
        for (String nodeAddr: channels.keySet()) {
            for (Channel conn: channels.get(nodeAddr)) {
                if (conn.isOpen()) {
                    conn.close();
                    closedConnCount ++;
                }
            }
        }
        channels.clear();
        bootstraps.clear();
        counts.clear();
        logger.fine("closed " + closedConnCount + " connections");
    }
}
