package org.xbib.netty.http.client.handler.http;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.ClientConfig;
import org.xbib.netty.http.client.handler.ws1.Http1WebSocketClientHandler;
import org.xbib.netty.http.common.HttpChannelInitializer;
import org.xbib.netty.http.client.handler.http2.Http2ChannelInitializer;
import org.xbib.netty.http.common.HttpAddress;

import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Http1ChannelInitializer extends ChannelInitializer<Channel> implements HttpChannelInitializer {

    private static final Logger logger = Logger.getLogger(Http1ChannelInitializer.class.getName());

    private final ClientConfig clientConfig;

    private final HttpAddress httpAddress;

    private final Client.SslHandlerFactory sslHandlerFactory;

    private final HttpResponseHandler httpResponseHandler;

    private final Http2ChannelInitializer http2ChannelInitializer;

    public Http1ChannelInitializer(ClientConfig clientConfig,
                                   HttpAddress httpAddress,
                                   Client.SslHandlerFactory sslHandlerFactory,
                                   HttpChannelInitializer http2ChannelInitializer) {
        this.clientConfig = clientConfig;
        this.httpAddress = httpAddress;
        this.sslHandlerFactory = sslHandlerFactory;
        this.http2ChannelInitializer = (Http2ChannelInitializer) http2ChannelInitializer;
        this.httpResponseHandler = new HttpResponseHandler();
    }

    @Override
    public void initChannel(Channel channel) {
        if (clientConfig.isDebug()) {
            channel.pipeline().addLast(new TrafficLoggingHandler(LogLevel.DEBUG));
        }
        if (httpAddress.isSecure()) {
            configureEncrypted(channel);
        } else {
            configureCleartext(channel);
        }
        if (clientConfig.isDebug()) {
            logger.log(Level.FINE, "HTTP 1.1 client channel initialized: " +
                    " address=" + httpAddress + " pipeline=" + channel.pipeline().names());
        }
    }

    private void configureEncrypted(Channel channel)  {
        ChannelPipeline pipeline = channel.pipeline();
        SslHandler sslHandler = sslHandlerFactory.create();
        pipeline.addLast("client-ssl-handler", sslHandler);
        if (clientConfig.isEnableNegotiation()) {
            ApplicationProtocolNegotiationHandler negotiationHandler =
                    new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                @Override
                protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                    if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                        http2ChannelInitializer.configureCleartext(ctx.channel());
                        if (clientConfig.isDebug()) {
                            logger.log(Level.FINE, "after negotiation to HTTP/2: " + ctx.pipeline().names());
                        }
                        return;
                    }
                    if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
                        configureCleartext(ctx.channel());
                        if (clientConfig.isDebug()) {
                            logger.log(Level.FINE, "after negotiation to HTTP 1.1: " + ctx.pipeline().names());
                        }
                        return;
                    }
                    ctx.close();
                    throw new IllegalStateException("protocol not accepted: " + protocol);
                }
            };
            channel.pipeline().addLast("client-negotiation", negotiationHandler);
        } else {
            configureCleartext(channel);
        }
    }

    private void configureCleartext(Channel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast("http-client-chunk-writer",
                new ChunkedWriteHandler());
        pipeline.addLast("http-client-codec",
                new HttpClientCodec(clientConfig.getMaxInitialLineLength(),
                 clientConfig.getMaxHeadersSize(), clientConfig.getMaxChunkSize()));
        if (clientConfig.isEnableGzip()) {
            pipeline.addLast("http-client-decompressor", new HttpContentDecompressor());
        }
        HttpObjectAggregator httpObjectAggregator =
                new HttpObjectAggregator(clientConfig.getMaxContentLength(), false);
        httpObjectAggregator.setMaxCumulationBufferComponents(clientConfig.getMaxCompositeBufferComponents());
        pipeline.addLast("http-client-aggregator",
                httpObjectAggregator);
        //pipeline.addLast( "http-client-ws-protocol-handler",
        //        new Http1WebSocketClientHandler(WebSocketClientHandshakerFactory.newHandshaker(URI.create("/websocket"),
        //                WebSocketVersion.V13, null, false, new DefaultHttpHeaders())));
        pipeline.addLast("http-client-handler",
                httpResponseHandler);
    }
}
