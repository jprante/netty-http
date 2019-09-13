package org.xbib.netty.http.server.endpoint.service;

import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClassLoaderService extends ResourceService {

    private static final Logger logger = Logger.getLogger(ClassLoaderService.class.getName());

    private final Class<?> clazz;

    private final String prefix;

    private final String indexFileName;

    public ClassLoaderService(Class<?> clazz, String prefix) {
        this(clazz, prefix, "index.html");
    }

    public ClassLoaderService(Class<?> clazz, String prefix, String indexFileName) {
        this.clazz = clazz;
        this.prefix = prefix;
        this.indexFileName = indexFileName;
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
            String effectivePath = serverRequest.getEffectiveRequestPath();
            this.resourcePath = effectivePath.startsWith("/") ? effectivePath.substring(1) : effectivePath;
            String path = prefix.endsWith("/") ? prefix : prefix + "/";
            path = resourcePath.startsWith("/") ? path + resourcePath.substring(1) : path + resourcePath;
            this.url = clazz.getResource(path);
            if (url != null) {
                URLConnection urlConnection = url.openConnection();
                this.lastModified = Instant.ofEpochMilli(urlConnection.getLastModified());
                this.length = urlConnection.getContentLength();
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "success: path=[" + path +
                            "] -> url=" + url + " lastModified=" + lastModified + "length=" + length);
                }
            } else {
                this.lastModified = Instant.now();
                this.length = 0;
                logger.log(Level.FINER, "fail: resource not found, url=" + url);
            }
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

        @Override
        public boolean isDirectory() {
            return resourcePath.endsWith("/");
        }

        @Override
        public String indexFileName() {
            return indexFileName;
        }
    }
}
