package org.xbib.netty.http.server.reactive;

import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpVersion;

class DelegateHttpMessage implements HttpMessage {
    protected final HttpMessage message;

    public DelegateHttpMessage(HttpMessage message) {
        this.message = message;
    }

    @Override
    @Deprecated
    public HttpVersion getProtocolVersion() {
        return message.protocolVersion();
    }

    @Override
    public HttpVersion protocolVersion() {
        return message.protocolVersion();
    }

    @Override
    public HttpMessage setProtocolVersion(HttpVersion version) {
        message.setProtocolVersion(version);
        return this;
    }

    @Override
    public HttpHeaders headers() {
        return message.headers();
    }

    @Override
    @Deprecated
    public DecoderResult getDecoderResult() {
        return message.decoderResult();
    }

    @Override
    public DecoderResult decoderResult() {
        return message.decoderResult();
    }

    @Override
    public void setDecoderResult(DecoderResult result) {
        message.setDecoderResult(result);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "(" + message.toString() + ")";
    }

}
