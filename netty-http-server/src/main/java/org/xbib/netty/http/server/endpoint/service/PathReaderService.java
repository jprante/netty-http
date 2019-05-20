package org.xbib.netty.http.server.endpoint.service;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class PathReaderService implements Service {

    private Path path;

    private ByteBufAllocator allocator;

    public PathReaderService(Path path, ByteBufAllocator allocator) {
        this.path = path;
        this.allocator = allocator;
    }

    @Override
    public void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        ByteBuf byteBuf = read(allocator, path.resolve(serverRequest.getEffectiveRequestPath()));
        try {
            serverResponse.write(HttpResponseStatus.OK, "application/octet-stream", byteBuf);
        } finally {
            byteBuf.release();
        }
    }

    private static ByteBuf read(ByteBufAllocator allocator, Path path) throws IOException {
        try (SeekableByteChannel sbc = Files.newByteChannel(path);
             InputStream in = Channels.newInputStream(sbc)) {
            int size = Math.toIntExact(sbc.size());
            ByteBuf byteBuf = allocator.directBuffer(size, size);
            byteBuf.writeBytes(in, size);
            return byteBuf;
        }
    }
}
