package org.xbib.netty.http.server.protocol.http1;

import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCounted;

public class HttpPipelinedRequest implements ReferenceCounted {

    private final LastHttpContent request;

    private final int sequenceId;

    public HttpPipelinedRequest(LastHttpContent request, int sequenceId) {
        this.request = request;
        this.sequenceId = sequenceId;
    }

    public HttpPipelinedResponse createHttpResponse(FullHttpResponse response, ChannelPromise promise) {
        return new HttpPipelinedResponse(response, promise, sequenceId);
    }

    public LastHttpContent getRequest() {
        return request;
    }

    public int getSequenceId() {
        return sequenceId;
    }

    @Override
    public int refCnt() {
        return request.refCnt();
    }

    @Override
    public ReferenceCounted retain() {
        request.retain();
        return this;
    }

    @Override
    public ReferenceCounted retain(int increment) {
        request.retain(increment);
        return this;
    }

    @Override
    public ReferenceCounted touch() {
        request.touch();
        return this;
    }

    @Override
    public ReferenceCounted touch(Object hint) {
        request.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return request.release();
    }

    @Override
    public boolean release(int decrement) {
        return request.release(decrement);
    }
}
