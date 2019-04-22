package org.xbib.netty.http.server.context;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.xbib.netty.http.server.transport.ServerRequest;
import org.xbib.netty.http.server.transport.ServerResponse;
import org.xbib.netty.http.server.util.MimeTypeUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ClasspathContextHandler implements ContextHandler {

    private final ClassLoader classLoader;

    private final String prefix;

    public ClasspathContextHandler(ClassLoader classLoader, String prefix) {
        this.classLoader = classLoader;
        this.prefix = prefix;
    }

    @Override
    public void serve(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        String contextPath = serverRequest.getContextPath();
        URL url = classLoader.getResource(prefix + contextPath);
        if (url != null) {
            try {
                Path path = Paths.get(url.toURI());
                FileChannel fileChannel = (FileChannel) Files.newByteChannel(path);
                MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
                ByteBuf byteBuf = Unpooled.wrappedBuffer(mappedByteBuffer);
                try {
                    String contentType = MimeTypeUtils.guessFromPath(contextPath, false);
                    serverResponse.write(HttpResponseStatus.OK, contentType, byteBuf);
                } finally {
                    byteBuf.release();
                }
            } catch (URISyntaxException e) {
                serverResponse.write(HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            serverResponse.write(HttpResponseStatus.NOT_FOUND);
        }
    }
}
