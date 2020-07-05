package org.xbib.netty.http.common;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;

public interface HttpChannelInitializer extends ChannelHandler {

    void initChannel(Channel channel);
}
