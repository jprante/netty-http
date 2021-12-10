package org.xbib.netty.http.server.protocol.ws2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;

public class Http2WebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final SslContext sslContext;

    Http2WebSocketChannelInitializer(SslContext sslContext) {
        this.sslContext = sslContext;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        SslHandler sslHandler = sslContext.newHandler(ch.alloc());
        Http2FrameCodec http2frameCodec = Http2WebSocketServerBuilder
                        .configureHttp2Server(Http2FrameCodecBuilder.forServer())
                        .build();
        ServerWebSocketHandler serverWebSocketHandler = new ServerWebSocketHandler();
        Http2WebSocketServerHandler http2webSocketHandler =
                Http2WebSocketServerBuilder.create()
                        .decoderConfig(WebSocketDecoderConfig.newBuilder().allowExtensions(true).build())
                        .compression(true)
                        .acceptor(new PathAcceptor("/test", serverWebSocketHandler))
                        .build();
        ch.pipeline().addLast(sslHandler, http2frameCodec, http2webSocketHandler);
    }

    @Sharable
    private static class ServerWebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame webSocketFrame) {
            // echo
            ctx.writeAndFlush(webSocketFrame.retain());
        }
    }
}