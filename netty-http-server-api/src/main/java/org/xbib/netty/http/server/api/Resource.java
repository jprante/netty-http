package org.xbib.netty.http.server.api;

import java.net.URL;
import java.time.Instant;

public interface Resource {

    String getResourcePath();

    URL getURL();

    Instant getLastModified();

    long getLength();

    boolean isDirectory();

    String indexFileName();
}
