package org.xbib.netty.http.server.endpoint.service;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.util.MimeTypeUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClasspathService implements Service {

    private static final Logger logger = Logger.getLogger(ClasspathService.class.getName());

    private Class<?> clazz;

    private final String prefix;

    private final Map<String, String> env;

    public ClasspathService(Class<?> clazz, String prefix) {
        this.clazz = clazz;
        this.prefix = prefix;
        this.env = new HashMap<>();
        env.put("create", "true");
    }

    @Override
    public void handle(ServerRequest serverRequest, ServerResponse serverResponse) {
        String requestPath = serverRequest.getEffectiveRequestPath();
        String contentType = MimeTypeUtils.guessFromPath(requestPath, false);
        URL url = clazz.getResource(prefix + "/" + requestPath);
        if (url != null) {
            try {
                if ("jar".equals(url.getProtocol())) {
                    doJarResource(url.toURI(), contentType, serverResponse);
                } else {
                    doFileResource(url.toURI(), contentType, serverResponse);
                }
            } catch (IOException | URISyntaxException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
                serverResponse.write(HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            serverResponse.write(HttpResponseStatus.NOT_FOUND);
        }
    }

    private void doFileResource(URI uri, String contentType, ServerResponse serverResponse) {
        try {
            FileChannel fileChannel = (FileChannel) Files.newByteChannel(Paths.get(uri));
            MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
            ByteBuf byteBuf = Unpooled.wrappedBuffer(mappedByteBuffer);
            serverResponse.write(HttpResponseStatus.OK, contentType, byteBuf);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            serverResponse.write(HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @SuppressWarnings("try")
    private void doJarResource(URI uri, String contentType, ServerResponse serverResponse) throws IOException {
        FileSystem zipfs = null;
        try {
            try {
                zipfs = FileSystems.getFileSystem(uri);
            } catch (FileSystemNotFoundException e) {
                zipfs = FileSystems.newFileSystem(uri, env);
            }
            ByteBuf byteBuf = Unpooled.wrappedBuffer(Files.readAllBytes(Paths.get(uri)));
            serverResponse.write(HttpResponseStatus.OK, contentType, byteBuf);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            serverResponse.write(HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }


}
