package org.xbib.netty.http.server.endpoint.service;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.util.MimeTypeUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChunkedFileService implements Service {

    private static final Logger logger = Logger.getLogger(ChunkedFileService.class.getName());

    private final Path prefix;

    public ChunkedFileService(Path prefix) {
        this.prefix = prefix;
        if (!Files.exists(prefix)) {
            throw new IllegalArgumentException("prefix: " + prefix + " (does not exist)");
        }
        if (!Files.exists(prefix) || !Files.isDirectory(prefix)) {
            throw new IllegalArgumentException("prefix: " + prefix + " (not a directory)");
        }
    }

    @Override
    public void handle(ServerRequest serverRequest, ServerResponse serverResponse) {
        String requestPath = serverRequest.getEffectiveRequestPath().substring(1); // always starts with '/'
        Path path = prefix.resolve(requestPath);
        if (Files.isReadable(path)) {
            try (InputStream inputStream = Files.newInputStream(path);
                    ReadableByteChannel byteChannel = Channels.newChannel(inputStream)) {
                String contentType = MimeTypeUtils.guessFromPath(requestPath, false);
                serverResponse.write(HttpResponseStatus.OK, contentType, byteChannel);
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
                ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
            }
        } else {
            logger.log(Level.WARNING, "failed to access path " + path + " prefix = " + prefix + " requestPath=" + requestPath);
            ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
        }
    }
}
