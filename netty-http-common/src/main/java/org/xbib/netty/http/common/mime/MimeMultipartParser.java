package org.xbib.netty.http.common.mime;

import java.io.IOException;

public interface MimeMultipartParser {

    String type();

    String subType();

    void parse(MimeMultipartListener listener) throws IOException;
}
