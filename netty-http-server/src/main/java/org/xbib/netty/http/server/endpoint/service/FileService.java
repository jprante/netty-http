package org.xbib.netty.http.server.endpoint.service;

import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public class FileService extends ResourceService {

    private final Path prefix;

    public FileService(Path prefix) {
        this.prefix = prefix;
        if (!Files.exists(prefix)) {
            throw new IllegalArgumentException("prefix: " + prefix + " (does not exist)");
        }
        if (!Files.isDirectory(prefix)) {
            throw new IllegalArgumentException("prefix: " + prefix + " (not a directory)");
        }
    }

    @Override
    protected Resource createResource(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        return new ChunkedFileResource(serverRequest);
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

    class ChunkedFileResource implements Resource {

        private final String resourcePath;

        private final URL url;

        private final Instant lastModified;

        private final long length;

        ChunkedFileResource(ServerRequest serverRequest) throws IOException {
            this.resourcePath = serverRequest.getEffectiveRequestPath().substring(1);
            Path path = prefix.resolve(resourcePath);
            this.url = path.toUri().toURL();
            this.lastModified = Files.getLastModifiedTime(path).toInstant();
            this.length = Files.size(path);
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
