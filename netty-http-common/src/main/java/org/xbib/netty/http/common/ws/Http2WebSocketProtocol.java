package org.xbib.netty.http.common.ws;

import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;

public final class Http2WebSocketProtocol {

    public static final char SETTINGS_ENABLE_CONNECT_PROTOCOL = 8;

    public static final AsciiString HEADER_METHOD_CONNECT = AsciiString.of("CONNECT");

    public static final AsciiString HEADER_PROTOCOL_NAME = AsciiString.of(":protocol");

    public static final AsciiString HEADER_PROTOCOL_VALUE = AsciiString.of("websocket");

    public static final AsciiString SCHEME_HTTP = AsciiString.of("http");

    public static final AsciiString SCHEME_HTTPS = AsciiString.of("https");

    public static final AsciiString HEADER_WEBSOCKET_VERSION_NAME = AsciiString.of("sec-websocket-version");

    public static final AsciiString HEADER_WEBSOCKET_VERSION_VALUE = AsciiString.of("13");

    public static final AsciiString HEADER_WEBSOCKET_SUBPROTOCOL_NAME = AsciiString.of("sec-websocket-protocol");

    public static final AsciiString HEADER_WEBSOCKET_EXTENSIONS_NAME = AsciiString.of("sec-websocket-extensions");

    public static final AsciiString HEADER_PROTOCOL_NAME_HANDSHAKED = AsciiString.of("x-protocol");

    public static final AsciiString HEADER_METHOD_CONNECT_HANDSHAKED = AsciiString.of("POST");

    public static Http2Headers extendedConnect(Http2Headers headers) {
        return headers.method(Http2WebSocketProtocol.HEADER_METHOD_CONNECT)
                .set(Http2WebSocketProtocol.HEADER_PROTOCOL_NAME, Http2WebSocketProtocol.HEADER_PROTOCOL_VALUE);
    }

    public static boolean isExtendedConnect(Http2Headers headers) {
        return HEADER_METHOD_CONNECT.equals(headers.method())
                && HEADER_PROTOCOL_VALUE.equals(headers.get(HEADER_PROTOCOL_NAME));
    }
}
