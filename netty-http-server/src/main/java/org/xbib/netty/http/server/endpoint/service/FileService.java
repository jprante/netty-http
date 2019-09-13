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

    private final String indexFileName;

    public FileService(Path prefix) {
        this(prefix, "index.html");
    }

    public FileService(Path prefix, String indexFileName) {
        this.prefix = prefix;
        this.indexFileName = indexFileName;
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

        private final boolean isDirectory;

        private final Instant lastModified;

        private final long length;

        ChunkedFileResource(ServerRequest serverRequest) throws IOException {
            String effectivePath = serverRequest.getEffectiveRequestPath();
            this.resourcePath = effectivePath.startsWith("/") ? effectivePath.substring(1) : effectivePath;
            Path path = prefix.resolve(resourcePath);
            this.url = path.toUri().toURL();
            boolean isExists = Files.exists(path);
            this.isDirectory = Files.isDirectory(path);
            if (isExists) {
                this.lastModified = Files.getLastModifiedTime(path).toInstant();
                this.length = Files.size(path);
            } else {
                this.lastModified = Instant.now();
                this.length = 0;
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
        public boolean isDirectory() {
            return isDirectory;
        }

        @Override
        public String indexFileName() {
            return indexFileName;
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
