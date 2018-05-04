package org.xbib.netty.http.server.handler.http;

import io.netty.handler.codec.http.LastHttpContent;

public class HttpPipelinedRequest {

    private final LastHttpContent request;

    private final int sequenceId;

    public HttpPipelinedRequest(LastHttpContent request, int sequenceId) {
        this.request = request;
        this.sequenceId = sequenceId;
    }

    public LastHttpContent getRequest() {
        return request;
    }

    public int getSequenceId() {
        return sequenceId;
    }
}
