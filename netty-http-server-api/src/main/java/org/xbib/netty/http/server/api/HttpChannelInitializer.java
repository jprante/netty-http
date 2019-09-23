package org.xbib.netty.http.server.api;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;

public interface HttpChannelInitializer extends ChannelHandler {

    void initChannel(Channel channel);

}
