package org.xbib.netty.http.server.protocol.http1;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.Mapping;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.common.HttpChannelInitializer;
import org.xbib.netty.http.server.api.ServerConfig;
import org.xbib.netty.http.server.handler.ExtendedSNIHandler;
import org.xbib.netty.http.server.handler.IdleTimeoutHandler;
import org.xbib.netty.http.server.handler.TrafficLoggingHandler;
import org.xbib.netty.http.server.api.ServerTransport;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Http1ChannelInitializer extends ChannelInitializer<Channel>
        implements HttpChannelInitializer {

    private static final Logger logger = Logger.getLogger(Http1ChannelInitializer.class.getName());

    private final Server server;

    private final ServerConfig serverConfig;

    private final HttpAddress httpAddress;

    private final Mapping<String, SslContext> domainNameMapping;

    public Http1ChannelInitializer(Server server,
                                   HttpAddress httpAddress,
                                   Mapping<String, SslContext> domainNameMapping) {
        this.server = server;
        this.serverConfig = server.getServerConfig();
        this.httpAddress = httpAddress;
        this.domainNameMapping = domainNameMapping;
    }

    @Override
    public void initChannel(Channel channel) {
        ServerTransport transport = server.newTransport(httpAddress.getVersion());
        channel.attr(ServerTransport.TRANSPORT_ATTRIBUTE_KEY).set(transport);
        if (serverConfig.isDebug()) {
            channel.pipeline().addLast(new TrafficLoggingHandler(LogLevel.DEBUG));
        }
        if (httpAddress.isSecure()) {
            configureEncrypted(channel);
        } else {
            configureCleartext(channel);
        }
        if (serverConfig.isDebug()) {
            logger.log(Level.FINE, "HTTP 1.1 server channel initialized: " +
                    " address=" + httpAddress + " pipeline=" + channel.pipeline().names());
        }
    }

    private void configureEncrypted(Channel channel)  {
        channel.pipeline().addLast("sni-handler",
                new ExtendedSNIHandler(domainNameMapping, serverConfig, httpAddress));
        configureCleartext(channel);
    }

    private void configureCleartext(Channel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast("http-server-read-timeout",
                new ReadTimeoutHandler(serverConfig.getReadTimeoutMillis()));
        pipeline.addLast("http-server-chunked-write",
                new ChunkedWriteHandler());
        pipeline.addLast("http-server-codec",
                new HttpServerCodec(serverConfig.getMaxInitialLineLength(),
                        serverConfig.getMaxHeadersSize(), serverConfig.getMaxChunkSize()));
        if (serverConfig.isCompressionEnabled()) {
            pipeline.addLast("http-server-compressor",
                    new HttpContentCompressor());
        }
        if (serverConfig.isDecompressionEnabled()) {
            pipeline.addLast("http-server-decompressor",
                    new HttpContentDecompressor());
        }
        HttpObjectAggregator httpObjectAggregator =
                new HttpObjectAggregator(serverConfig.getMaxContentLength());
        httpObjectAggregator.setMaxCumulationBufferComponents(serverConfig.getMaxCompositeBufferComponents());
        pipeline.addLast("http-server-aggregator",
                httpObjectAggregator);
        if (serverConfig.getWebSocketFrameHandler() != null) {
            pipeline.addLast("http-server-ws-protocol-handler",
                    new WebSocketServerProtocolHandler("/websocket"));
            pipeline.addLast("http-server-ws-handler",
                    serverConfig.getWebSocketFrameHandler());
        }
        if (serverConfig.isPipeliningEnabled()) {
            pipeline.addLast("http-server-pipelining",
                    new HttpPipeliningHandler(serverConfig.getPipeliningCapacity()));
        }
        pipeline.addLast("http-server-handler",
                new ServerMessages(server));
        pipeline.addLast("http-idle-timeout-handler",
                new IdleTimeoutHandler(serverConfig.getIdleTimeoutMillis()));
    }

    @Sharable
    class ServerMessages extends ChannelInboundHandlerAdapter {

        private final Logger logger = Logger.getLogger(ServerMessages.class.getName());

        private final Server server;

        public ServerMessages(Server server) {
            this.server = server;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof WebSocketFrame) {
                WebSocketFrame webSocketFrame = (WebSocketFrame) msg;
                if (serverConfig.getWebSocketFrameHandler() != null) {
                    serverConfig.getWebSocketFrameHandler().channelRead(ctx, webSocketFrame);
                }
               return;
            }
            if (msg instanceof HttpPipelinedRequest) {
                HttpPipelinedRequest httpPipelinedRequest = (HttpPipelinedRequest) msg;
                if (httpPipelinedRequest.getRequest() instanceof FullHttpRequest) {
                    FullHttpRequest fullHttpRequest = (FullHttpRequest) httpPipelinedRequest.getRequest();
                    if (fullHttpRequest.protocolVersion().majorVersion() == 2) {
                        // PRI * HTTP/2.0
                        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                                HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED);
                        ctx.channel().writeAndFlush(response);
                    } else {
                        ServerTransport transport = server.newTransport(fullHttpRequest.protocolVersion());
                        transport.requestReceived(ctx, fullHttpRequest, httpPipelinedRequest.getSequenceId());
                    }
                }
                if (httpPipelinedRequest.refCnt() > 0) {
                    httpPipelinedRequest.release();
                }
            } else if (msg instanceof FullHttpRequest){
                FullHttpRequest fullHttpRequest = (FullHttpRequest) msg;
                if (fullHttpRequest.protocolVersion().majorVersion() == 2) {
                    // PRI * HTTP/2.0
                    DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                            HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED);
                    ctx.channel().writeAndFlush(response);
                } else {
                    ServerTransport transport = server.newTransport(fullHttpRequest.protocolVersion());
                    transport.requestReceived(ctx, fullHttpRequest, 0);
                }
                fullHttpRequest.release();
            } else {
                super.channelRead(ctx, msg);
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.log(Level.SEVERE, cause.getMessage(), cause);
            ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    Unpooled.copiedBuffer(cause.getMessage().getBytes(StandardCharsets.UTF_8))));
        }
    }
}
