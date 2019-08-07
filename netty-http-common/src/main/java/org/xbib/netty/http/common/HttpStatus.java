package org.xbib.netty.http.common;

import io.netty.handler.codec.http.HttpResponseStatus;

public class HttpStatus {

    private final HttpResponseStatus httpResponseStatus;

    public HttpStatus(HttpResponseStatus httpResponseStatus) {
        this.httpResponseStatus = httpResponseStatus;
    }

    public int getCode() {
        return httpResponseStatus.code();
    }

    public String getMessage() {
        return httpResponseStatus.codeAsText().toString();
    }

    public String getReasonPhrase() {
        return httpResponseStatus.reasonPhrase();
    }

    @Override
    public String toString() {
        return httpResponseStatus.toString();
    }

}
