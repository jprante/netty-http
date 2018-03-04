package org.xbib.netty.http.client.pool;

import java.net.InetSocketAddress;

public interface PoolKey {

    InetSocketAddress getInetSocketAddress();
}
