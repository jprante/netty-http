package org.xbib.netty.http.common.ws;

public interface Http2WebSocketMessages {
    String HANDSHAKE_UNEXPECTED_RESULT =
            "websocket handshake error: unexpected result - status=200, end_of_stream=true";
    String HANDSHAKE_UNSUPPORTED_VERSION =
            "websocket handshake error: unsupported version; supported versions - ";
    String HANDSHAKE_BAD_REQUEST =
            "websocket handshake error: bad request";
    String HANDSHAKE_PATH_NOT_FOUND =
            "websocket handshake error: path not found - ";
    String HANDSHAKE_PATH_NOT_FOUND_SUBPROTOCOLS =
            ", subprotocols - ";
    String HANDSHAKE_UNEXPECTED_SUBPROTOCOL =
            "websocket handshake error: unexpected subprotocol - ";
    String HANDSHAKE_GENERIC_ERROR =
            "websocket handshake error: ";
    String HANDSHAKE_UNSUPPORTED_ACCEPTOR_TYPE =
            "websocket handshake error: async acceptors are not supported";
    String HANDSHAKE_UNSUPPORTED_BOOTSTRAP =
            "websocket handshake error: bootstrapping websockets with http2 is not supported by server";
    String HANDSHAKE_INVALID_REQUEST_HEADERS =
            "websocket handshake error: invalid request headers";
    String HANDSHAKE_INVALID_RESPONSE_HEADERS =
            "websocket handshake error: invalid response headers";
    String WRITE_ERROR = "websocket frame write error";
}
