package org.xbib.netty.http.common;

import java.io.IOException;
import java.io.InputStream;

public interface HttpResponse {

    int getStatusCode() throws IOException;

    String getReason() throws Exception;

    InputStream getContent() throws IOException;
}
