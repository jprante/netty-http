package org.xbib.netty.http.server.endpoint.service;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.util.MimeTypeUtils;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MappedFileService implements Service {

    private static final Logger logger = Logger.getLogger(MappedFileService.class.getName());

    private final Path prefix;

    public MappedFileService(Path prefix) {
        this.prefix = prefix;
        if (!Files.exists(prefix)) {
            throw new IllegalArgumentException("prefix: " + prefix + " (does not exist)");
        }
        if (!Files.isDirectory(prefix)) {
            throw new IllegalArgumentException("prefix: " + prefix + " (not a directory)");
        }
    }

    @Override
    public void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        String requestPath = serverRequest.getEffectiveRequestPath().substring(1); // always starts with '/'
        Path path = prefix.resolve(requestPath);
        if (Files.isReadable(path)) {
            try (FileChannel fileChannel = (FileChannel) Files.newByteChannel(path)) {
                MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
                ByteBuf byteBuf = Unpooled.wrappedBuffer(mappedByteBuffer);
                String contentType = MimeTypeUtils.guessFromPath(requestPath, false);
                serverResponse.write(HttpResponseStatus.OK, contentType, byteBuf);
            }
        } else {
            logger.log(Level.WARNING, "failed to access path " + path + " prefix = " + prefix + " requestPath=" + requestPath);
            ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
        }
    }
}
