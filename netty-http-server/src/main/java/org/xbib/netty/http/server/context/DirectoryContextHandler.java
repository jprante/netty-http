package org.xbib.netty.http.server.context;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.xbib.netty.http.server.transport.ServerRequest;
import org.xbib.netty.http.server.transport.ServerResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class DirectoryContextHandler implements ContextHandler {

    private Path path;

    private ByteBufAllocator allocator;

    public DirectoryContextHandler(Path path, ByteBufAllocator allocator) {
        this.path = path;
        this.allocator = allocator;
    }

    @Override
    public void serve(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        String uri = serverRequest.getRequest().uri();
        Path p = path.resolve(uri);
        ByteBuf byteBuf = read(allocator, p);
        serverResponse.write(HttpResponseStatus.OK, "application/octet-stream", byteBuf);
        byteBuf.release();
    }

    public static ByteBuf read(ByteBufAllocator allocator, Path path)
            throws IOException {
        try (SeekableByteChannel sbc = Files.newByteChannel(path);
             InputStream in = Channels.newInputStream(sbc)) {
            int size = Math.toIntExact(sbc.size());
            ByteBuf byteBuf = allocator.directBuffer(size, size);
            byteBuf.writeBytes(in, size);
            return byteBuf;
        }
    }
}
