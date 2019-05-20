package org.xbib.netty.http.server.endpoint.service;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.util.MimeTypeUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ClasspathService implements Service {

    private final ClassLoader classLoader;

    private final String prefix;

    public ClasspathService(ClassLoader classLoader, String prefix) {
        this.classLoader = classLoader;
        this.prefix = prefix;
    }

    @Override
    public void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        String requestPath = serverRequest.getEffectiveRequestPath();
        URL url = classLoader.getResource(prefix + requestPath);
        if (url != null) {
            try {
                FileChannel fileChannel = (FileChannel) Files.newByteChannel(Paths.get(url.toURI()));
                MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
                ByteBuf byteBuf = Unpooled.wrappedBuffer(mappedByteBuffer);
                try {
                    String contentType = MimeTypeUtils.guessFromPath(requestPath, false);
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
