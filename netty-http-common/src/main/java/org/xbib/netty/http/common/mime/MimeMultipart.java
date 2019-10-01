package org.xbib.netty.http.common.mime;

import io.netty.buffer.ByteBuf;
import java.util.Map;

public interface MimeMultipart {

    Map headers();

    ByteBuf body();

    int length();
}
