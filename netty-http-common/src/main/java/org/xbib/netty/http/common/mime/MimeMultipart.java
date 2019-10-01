package org.xbib.netty.http.common.mime;

import io.netty.buffer.ByteBuf;
import java.util.Map;

public interface MimeMultipart {

    Map<String, String> headers();

    ByteBuf body();

    int length();
}
