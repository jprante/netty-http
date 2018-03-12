package org.xbib.netty.http.server.handler.http1;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.DomainNameMapping;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.ServerConfig;

import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger logger = Logger.getLogger(HttpChannelInitializer.class.getName());

    private final ServerConfig serverConfig;

    private final HttpAddress httpAddress;

    private final HttpHandler httpHandler;

    private final DomainNameMapping<SslContext> domainNameMapping;

    public HttpChannelInitializer(Server server,
                                  HttpAddress httpAddress,
                                  DomainNameMapping<SslContext> domainNameMapping) {
        this.serverConfig = server.getServerConfig();
        this.httpAddress = httpAddress;
        this.domainNameMapping = domainNameMapping;
        this.httpHandler = new HttpHandler(server);
    }

    @Override
    public void initChannel(SocketChannel channel) {
        if (serverConfig.isDebug()) {
            channel.pipeline().addLast(new TrafficLoggingHandler(LogLevel.DEBUG));
        }
        if (httpAddress.isSecure()) {
            configureEncrypted(channel);
        } else {
            configureCleartext(channel);
        }
        if (serverConfig.isDebug()) {
            logger.log(Level.FINE, "HTTP 1 channel initialized: " + channel.pipeline().names());
        }
    }

    private void configureEncrypted(SocketChannel channel)  {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(new SniHandler(domainNameMapping));
        configureCleartext(channel);
    }

    private void configureCleartext(SocketChannel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(new HttpServerCodec(serverConfig.getMaxInitialLineLength(),
                serverConfig.getMaxHeadersSize(), serverConfig.getMaxChunkSize()));
        if (serverConfig.isEnableGzip()) {
            pipeline.addLast(new HttpContentDecompressor());
        }
        HttpObjectAggregator httpObjectAggregator = new HttpObjectAggregator(serverConfig.getMaxContentLength(),
                false);
        httpObjectAggregator.setMaxCumulationBufferComponents(serverConfig.getMaxCompositeBufferComponents());
        pipeline.addLast(httpObjectAggregator);
        pipeline.addLast(httpHandler);
    }
}
