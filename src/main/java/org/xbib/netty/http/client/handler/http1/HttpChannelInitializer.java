package org.xbib.netty.http.client.handler.http1;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SslHandler;
import org.xbib.netty.http.client.ClientConfig;
import org.xbib.netty.http.client.HttpAddress;

import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger logger = Logger.getLogger(HttpChannelInitializer.class.getName());

    private final ClientConfig clientConfig;

    private final HttpAddress httpAddress;

    private final SslHandler sslHandler;

    private final HttpResponseHandler httpResponseHandler;

    public HttpChannelInitializer(ClientConfig clientConfig,
                                  HttpAddress httpAddress,
                                  SslHandler sslHandler,
                                  HttpResponseHandler httpResponseHandler) {
        this.clientConfig = clientConfig;
        this.httpAddress = httpAddress;
        this.sslHandler = sslHandler;
        this.httpResponseHandler = httpResponseHandler;
    }

    @Override
    public void initChannel(SocketChannel channel) {
        if (clientConfig.isDebug()) {
            channel.pipeline().addLast(new TrafficLoggingHandler(LogLevel.DEBUG));
        }
        if (httpAddress.isSecure()) {
            configureEncrypted(channel);
        } else {
            configureCleartext(channel);
        }
        if (clientConfig.isDebug()) {
            logger.log(Level.FINE, "HTTP 1 channel initialized: " + channel.pipeline().names());
        }
    }

    private void configureEncrypted(SocketChannel channel)  {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(sslHandler);
        configureCleartext(channel);
    }

    private void configureCleartext(SocketChannel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(new HttpClientCodec(clientConfig.getMaxInitialLineLength(),
                 clientConfig.getMaxHeadersSize(), clientConfig.getMaxChunkSize()));
        if (clientConfig.isEnableGzip()) {
            pipeline.addLast(new HttpContentDecompressor());
        }
        HttpObjectAggregator httpObjectAggregator = new HttpObjectAggregator(clientConfig.getMaxContentLength(),
                false);
        httpObjectAggregator.setMaxCumulationBufferComponents(clientConfig.getMaxCompositeBufferComponents());
        pipeline.addLast(httpObjectAggregator);
        /*if (clientConfig.isEnableGzip()) {
            pipeline.addLast(new HttpChunkContentCompressor(6));
        }
        pipeline.addLast(new ChunkedWriteHandler());*/
        pipeline.addLast(httpResponseHandler);
    }
}
