package org.xbib.netty.http.server.endpoint.service;

import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;

public class ClassLoaderService extends ResourceService {

    private final Class<?> clazz;

    private final String prefix;

    public ClassLoaderService(Class<?> clazz, String prefix) {
        this.clazz = clazz;
        this.prefix = prefix;
    }

    @Override
    protected Resource createResource(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        return new ClassLoaderResource(serverRequest);
    }

    @Override
    protected boolean isETagResponseEnabled() {
        return true;
    }

    @Override
    protected boolean isCacheResponseEnabled() {
        return true;
    }

    @Override
    protected boolean isRangeResponseEnabled() {
        return true;
    }

    class ClassLoaderResource implements Resource {

        private final String resourcePath;

        private final URL url;

        private final Instant lastModified;

        private final long length;

        ClassLoaderResource(ServerRequest serverRequest) throws IOException {
            this.resourcePath = serverRequest.getEffectiveRequestPath().substring(1);
            this.url = clazz.getResource(prefix + "/" + resourcePath);
            URLConnection urlConnection = url.openConnection();
            this.lastModified = Instant.ofEpochMilli(urlConnection.getLastModified());
            this.length = urlConnection.getContentLength();
        }

        @Override
        public String getResourcePath() {
            return resourcePath;
        }

        @Override
        public URL getURL() {
            return url;
        }

        @Override
        public Instant getLastModified() {
            return lastModified;
        }

        @Override
        public long getLength() {
            return length;
        }
    }
}
