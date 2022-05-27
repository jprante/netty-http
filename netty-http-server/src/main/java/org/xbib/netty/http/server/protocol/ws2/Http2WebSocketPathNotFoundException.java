package org.xbib.netty.http.server.protocol.ws2;

import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;

@SuppressWarnings("serial")
public final class Http2WebSocketPathNotFoundException extends WebSocketHandshakeException {

    public Http2WebSocketPathNotFoundException(String message) {
        super(message);
    }
}
