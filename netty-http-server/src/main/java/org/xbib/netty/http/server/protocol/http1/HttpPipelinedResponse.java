package org.xbib.netty.http.server.protocol.http1;

import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.ReferenceCounted;

public class HttpPipelinedResponse implements ReferenceCounted, Comparable<HttpPipelinedResponse> {

    private final FullHttpResponse response;

    private final ChannelPromise promise;

    private final int sequenceId;

    public HttpPipelinedResponse(FullHttpResponse response, ChannelPromise promise, int sequenceId) {
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
        return Integer.compare(this.sequenceId, other.sequenceId);
    }

    @Override
    public int refCnt() {
        return response.refCnt();
    }

    @Override
    public ReferenceCounted retain() {
        response.retain();
        return this;
    }

    @Override
    public ReferenceCounted retain(int increment) {
        response.retain(increment);
        return this;
    }

    @Override
    public ReferenceCounted touch() {
        response.touch();
        return this;
    }

    @Override
    public ReferenceCounted touch(Object hint) {
        response.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return response.release();
    }

    @Override
    public boolean release(int decrement) {
        return response.release(decrement);
    }
}
