package org.xbib.netty.http.server.endpoint.service;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.util.MimeTypeUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClassLoaderService implements Service {

    private static final Logger logger = Logger.getLogger(ClassLoaderService.class.getName());

    private Class<?> clazz;

    private final String prefix;

    public ClassLoaderService(Class<?> clazz, String prefix) {
        this.clazz = clazz;
        this.prefix = prefix;
    }

    @Override
    public void handle(ServerRequest serverRequest, ServerResponse serverResponse) {
        String requestPath = serverRequest.getEffectiveRequestPath().substring(1);
        String contentType = MimeTypeUtils.guessFromPath(requestPath, false);
        URL url = clazz.getResource(prefix + "/" + requestPath);
        if (url != null) {
            if ("file".equals(url.getProtocol())) {
                doMappedResource(url, contentType, serverResponse);
            } else {
                doResource(url, contentType, serverResponse);
            }
        } else {
            ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
        }
    }

    private void doMappedResource(URL url, String contentType, ServerResponse serverResponse) {
        try {
            FileChannel fileChannel = (FileChannel) Files.newByteChannel(Paths.get(url.toURI()));
            MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
            ByteBuf byteBuf = Unpooled.wrappedBuffer(mappedByteBuffer);
            serverResponse.write(HttpResponseStatus.OK, contentType, byteBuf);
        } catch (URISyntaxException | IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
        }
    }

    private void doResource(URL url, String contentType, ServerResponse serverResponse) {
        try (InputStream inputStream = url.openStream();
             ReadableByteChannel byteChannel = Channels.newChannel(inputStream)) {
            serverResponse.write(HttpResponseStatus.OK, contentType, byteChannel);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
        }
    }
}
