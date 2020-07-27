package org.xbib.netty.http.server;

import io.netty.handler.codec.http.HttpResponseStatus;

public enum AcceptState {

    OK(HttpResponseStatus.OK, null, null),
    MISSING_HOST_HEADER(HttpResponseStatus.BAD_REQUEST, "application/octet-stream", "missing 'Host' header"),
    EXPECTATION_FAILED(HttpResponseStatus.EXPECTATION_FAILED, null, null),
    UNSUPPORTED_HTTP_VERSION( HttpResponseStatus.BAD_REQUEST, "application/octet-stream", "unsupported HTTP version");

    HttpResponseStatus status;

    String contentType;

    String content;

    AcceptState(HttpResponseStatus status, String contentType, String content) {
        this.status = status;
        this.contentType = contentType;
        this.content = content;
    }
}
