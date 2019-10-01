package org.xbib.netty.http.common.mime;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MimePart implements MimeMultipart {

    Map<String, String> headers;

    ByteBuf body;

    int length;

    MimePart(Map<String, String> headers, ByteBuf body) {
        this.headers = headers;
        this.body = body;
        this.length = body.readableBytes();
    }

    @Override
    public Map<String, String> headers() {
        return headers;
    }

    @Override
    public ByteBuf body() {
        return body;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public String toString() {
        String b = body != null ? body.toString(StandardCharsets.UTF_8) : "";
        return "headers=" + headers + " length=" + length + " body=" + b;
    }
}
