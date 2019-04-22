package org.xbib.netty.http.server.context;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.xbib.netty.http.server.transport.ServerRequest;
import org.xbib.netty.http.server.transport.ServerResponse;
import org.xbib.netty.http.server.util.MimeTypeUtils;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class NioContextHandler implements ContextHandler {

    private final Path prefix;

    public NioContextHandler(Path prefix) {
        this.prefix = prefix;
        if (!Files.exists(prefix) || !Files.isDirectory(prefix)) {
            throw new IllegalArgumentException("prefix: " + prefix + " (not a directory");
        }
    }

    @Override
    public void serve(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        String requestPath = serverRequest.getRequestPath();
        Path path = prefix.resolve(requestPath.substring(1)); // starts always with '/'
        if (Files.exists(path) && Files.isReadable(path)) {
            try (FileChannel fileChannel = (FileChannel) Files.newByteChannel(path)) {
                MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
                ByteBuf byteBuf = Unpooled.wrappedBuffer(mappedByteBuffer);
                String contentType = MimeTypeUtils.guessFromPath(requestPath, false);
                serverResponse.write(HttpResponseStatus.OK, contentType, byteBuf);
            }
        } else {
            serverResponse.write(HttpResponseStatus.NOT_FOUND);
        }
    }
}
