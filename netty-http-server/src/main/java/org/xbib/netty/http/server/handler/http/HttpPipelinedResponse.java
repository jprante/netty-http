package org.xbib.netty.http.server.handler.http;

import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;

public class HttpPipelinedResponse implements Comparable<HttpPipelinedResponse> {

    private final HttpResponse response;
    private final ChannelPromise promise;
    private final int sequenceId;

    public HttpPipelinedResponse(HttpResponse response, ChannelPromise promise, int sequenceId) {
        this.response = response;
        this.promise = promise;
        this.sequenceId = sequenceId;
    }

    public int getSequenceId() {
        return sequenceId;
    }

    public HttpResponse getResponse() {
        return response;
    }

    public ChannelPromise getPromise() {
        return promise;
    }

    @Override
    public int compareTo(HttpPipelinedResponse other) {
        return this.sequenceId - other.sequenceId;
    }
}
