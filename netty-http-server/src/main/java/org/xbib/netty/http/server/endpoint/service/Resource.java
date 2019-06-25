package org.xbib.netty.http.server.endpoint.service;

import java.net.URL;
import java.time.Instant;

public interface Resource {

    String getResourcePath();

    URL getURL();

    Instant getLastModified();

    long getLength();
}
