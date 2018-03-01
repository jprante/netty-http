package org.xbib.netty.http.client.pool;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.io.Closeable;
import java.net.ConnectException;
import java.util.List;

public interface ChannelPool extends Closeable {

	AttributeKey<String> NODE_ATTRIBUTE_KEY = AttributeKey.valueOf("node");

	void prepare(int count) throws ConnectException;

	Channel lease() throws ConnectException;
	
	int lease(List<Channel> channels, int maxCount) throws ConnectException;

	void release(Channel channel);
	
	void release(List<Channel> channels);
}
