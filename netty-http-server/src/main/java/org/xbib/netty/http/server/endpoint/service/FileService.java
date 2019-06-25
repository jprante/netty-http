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

    /*@Override
    public void handle(ServerRequest serverRequest, ServerResponse serverResponse) {
        String requestPath = serverRequest.getEffectiveRequestPath().substring(1); // always starts with '/'
        Path path = prefix.resolve(requestPath);
        if (Files.isReadable(path)) {
            try (InputStream inputStream = Files.newInputStream(path);
                    ReadableByteChannel byteChannel = Channels.newChannel(inputStream)) {
                String contentType = MimeTypeUtils.guessFromPath(requestPath, false);
                serverResponse.write(HttpResponseStatus.OK, contentType, new ChunkedNioStream(byteChannel));
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
                ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
            }
        } else {
            logger.log(Level.WARNING, "failed to access path " + path + " prefix = " + prefix + " requestPath=" + requestPath);
            ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
        }
    }*/
}
