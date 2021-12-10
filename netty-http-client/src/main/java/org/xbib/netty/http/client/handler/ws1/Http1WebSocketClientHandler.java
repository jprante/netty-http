package org.xbib.netty.http.client.handler.ws1;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;

import java.io.IOException;

public class Http1WebSocketClientHandler extends ChannelInboundHandlerAdapter {
    
    final WebSocketClientHandshaker handshaker;

    public Http1WebSocketClientHandler(WebSocketClientHandshaker handshaker) {
        this.handshaker = handshaker;
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelActive();
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelInactive();
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) msg)
                      .addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.fireChannelRead(msg);
        }
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
            String actualProtocol = handshaker.actualSubprotocol();
            if (actualProtocol.equals("")) {
            }
            else {
                throw new IOException("Invalid Websocket Protocol");
            }
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }
}