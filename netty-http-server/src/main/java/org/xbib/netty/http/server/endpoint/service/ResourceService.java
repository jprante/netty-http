package org.xbib.netty.http.server.endpoint.service;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.stream.ChunkedNioStream;
import org.xbib.netty.http.common.util.DateTimeUtil;
import org.xbib.netty.http.server.api.Filter;
import org.xbib.netty.http.server.api.Resource;
import org.xbib.netty.http.server.api.ServerRequest;
import org.xbib.netty.http.server.api.ServerResponse;
import org.xbib.netty.http.server.util.MimeTypeUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ResourceService implements Filter {

    private static final Logger logger = Logger.getLogger(ResourceService.class.getName());

    @Override
    public void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        handleCachedResource(serverRequest, serverResponse, createResource(serverRequest, serverResponse));
    }

    protected abstract Resource createResource(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException;

    protected abstract boolean isETagResponseEnabled();

    protected abstract boolean isCacheResponseEnabled();

    protected abstract boolean isRangeResponseEnabled();

    protected abstract int getMaxAgeSeconds();

    private void handleCachedResource(ServerRequest serverRequest, ServerResponse serverResponse, Resource resource) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "resource = " + resource);
        }
        if (resource.isDirectory()) {
            if (!resource.getResourcePath().isEmpty() && !resource.getResourcePath().endsWith("/")) {
                // external redirect to
                serverResponse.withHeader(HttpHeaderNames.LOCATION, resource.getResourcePath() + "/");
                ServerResponse.write(serverResponse, HttpResponseStatus.MOVED_PERMANENTLY);
                return;
            } else if (resource.indexFileName() != null) {
                // external redirect to default index file in this directory
                serverResponse.withHeader(HttpHeaderNames.LOCATION, resource.indexFileName());
                ServerResponse.write(serverResponse, HttpResponseStatus.MOVED_PERMANENTLY);
                return;
            } else {
                // send forbidden, we do not allow directory access
                ServerResponse.write(serverResponse, HttpResponseStatus.FORBIDDEN);
                return;
            }
        }
        // if resource is length of 0, there is nothing to send. Do not send any content, just flush the status
        if (resource.getLength() == 0) {
            serverResponse.flush();
            return;
        }
        HttpHeaders headers = serverRequest.getHeaders();
        String contentType = MimeTypeUtils.guessFromPath(resource.getResourcePath(), false);
        long expirationMillis = System.currentTimeMillis() + 1000 * getMaxAgeSeconds();
        if (isCacheResponseEnabled()) {
            serverResponse.withHeader(HttpHeaderNames.EXPIRES, DateTimeUtil.formatRfc1123(expirationMillis))
                    .withHeader(HttpHeaderNames.CACHE_CONTROL, "public, max-age=" + getMaxAgeSeconds());
        }
        boolean sent = false;
        if (isETagResponseEnabled()) {
            Instant lastModifiedInstant = resource.getLastModified();
            String eTag = Long.toHexString(resource.getResourcePath().hashCode() + lastModifiedInstant.toEpochMilli() + resource.getLength());
            Instant ifUnmodifiedSinceInstant = DateTimeUtil.parseDate(headers.get(HttpHeaderNames.IF_UNMODIFIED_SINCE));
            if (ifUnmodifiedSinceInstant != null &&
                    ifUnmodifiedSinceInstant.plusMillis(1000L).isAfter(lastModifiedInstant)) {
                ServerResponse.write(serverResponse, HttpResponseStatus.PRECONDITION_FAILED);
                return;
            }
            String ifMatch = headers.get(HttpHeaderNames.IF_MATCH);
            if (ifMatch != null && !matches(ifMatch, eTag)) {
                ServerResponse.write(serverResponse, HttpResponseStatus.PRECONDITION_FAILED);
                return;
            }
            String ifNoneMatch = headers.get(HttpHeaderNames.IF_NONE_MATCH);
            if (ifNoneMatch != null && matches(ifNoneMatch, eTag)) {
                serverResponse.withHeader(HttpHeaderNames.ETAG, eTag)
                        .withHeader(HttpHeaderNames.EXPIRES, DateTimeUtil.formatRfc1123(expirationMillis));
                ServerResponse.write(serverResponse, HttpResponseStatus.NOT_MODIFIED);
                return;
            }
            Instant ifModifiedSinceInstant = DateTimeUtil.parseDate(headers.get(HttpHeaderNames.IF_MODIFIED_SINCE));
            if (ifModifiedSinceInstant != null &&
                    ifModifiedSinceInstant.plusMillis(1000L).isAfter(lastModifiedInstant)) {
                serverResponse.withHeader(HttpHeaderNames.ETAG, eTag)
                        .withHeader(HttpHeaderNames.EXPIRES, DateTimeUtil.formatRfc1123(expirationMillis));
                ServerResponse.write(serverResponse, HttpResponseStatus.NOT_MODIFIED);
                return;
            }
            serverResponse.withHeader(HttpHeaderNames.ETAG, eTag)
                    .withHeader(HttpHeaderNames.LAST_MODIFIED, DateTimeUtil.formatRfc1123(lastModifiedInstant));
            if (isRangeResponseEnabled()) {
                performRangeResponse(serverRequest, serverResponse, resource, contentType, eTag, headers);
                sent = true;
            }
        }
        if (!sent) {
            serverResponse.withHeader(HttpHeaderNames.CONTENT_LENGTH, Long.toString(resource.getLength()));
            send(resource.getURL(), contentType, serverRequest, serverResponse);
        }
    }

    private void performRangeResponse(ServerRequest serverRequest, ServerResponse serverResponse,
                                       Resource resource,
                                       String contentType, String eTag,
                                       HttpHeaders headers) {
        long length = resource.getLength();
        serverResponse.withHeader(HttpHeaderNames.ACCEPT_RANGES, "bytes");
        Range full = new Range(0, length - 1, length);
        List<Range> ranges = new ArrayList<>();
        String range = headers.get(HttpHeaderNames.RANGE);
        if (range != null) {
            if (!range.matches("^bytes=\\d*-\\d*(,\\d*-\\d*)*$")) {
                serverResponse.withHeader(HttpHeaderNames.CONTENT_RANGE, "bytes */" + length);
                ServerResponse.write(serverResponse, HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
                return;
            }
            String ifRange = headers.get(HttpHeaderNames.IF_RANGE);
            if (ifRange != null && !ifRange.equals(eTag)) {
                try {
                    Instant ifRangeTime = DateTimeUtil.parseDate(ifRange);
                    if (ifRangeTime != null && ifRangeTime.plusMillis(1000).isBefore(resource.getLastModified())) {
                        ranges.add(full);
                    }
                } catch (IllegalArgumentException ignore) {
                    ranges.add(full);
                }
            }
            if (ranges.isEmpty()) {
                for (String part : range.substring(6).split(",")) {
                    long start = sublong(part, 0, part.indexOf('-'));
                    long end = sublong(part, part.indexOf('-') + 1, part.length());
                    if (start == -1L) {
                        start = length - end;
                        end = length - 1;
                    } else if (end == -1L || end > length - 1) {
                        end = length - 1;
                    }
                    if (start > end) {
                        serverResponse.withHeader(HttpHeaderNames.CONTENT_RANGE, "bytes */" + length);
                        ServerResponse.write(serverResponse, HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
                        return;
                    }
                    ranges.add(new Range(start, end, length));
                }
            }
        }
        if (ranges.isEmpty() || ranges.get(0) == full) {
            serverResponse.withHeader(HttpHeaderNames.CONTENT_RANGE, "bytes " + full.start + '-' + full.end + '/' + full.total)
                    .withHeader(HttpHeaderNames.CONTENT_LENGTH, Long.toString(full.length));
            send(resource.getURL(), HttpResponseStatus.OK, contentType, serverRequest, serverResponse, full.start, full.length);
        } else if (ranges.size() == 1) {
            Range r = ranges.get(0);
            serverResponse.withHeader(HttpHeaderNames.CONTENT_RANGE, "bytes " + r.start + '-' + r.end + '/' + r.total)
                    .withHeader(HttpHeaderNames.CONTENT_LENGTH, Long.toString(r.length));
            send(resource.getURL(), HttpResponseStatus.PARTIAL_CONTENT, contentType, serverRequest, serverResponse, r.start, r.length);
        } else {
            serverResponse.withHeader(HttpHeaderNames.CONTENT_TYPE, "multipart/byteranges; boundary=MULTIPART_BOUNDARY");
            StringBuilder sb = new StringBuilder();
            for (Range r : ranges) {
                try {
                    sb.append('\n')
                        .append("--MULTIPART_BOUNDARY").append('\n')
                        .append("content-type: ").append(contentType).append('\n')
                        .append("content-range: bytes ").append(r.start).append('-').append(r.end).append('/').append(r.total).append('\n')
                        .append(StandardCharsets.ISO_8859_1.decode(readBuffer(resource.getURL(), r.start, r.length))).append('\n')
                        .append("--MULTIPART_BOUNDARY--").append('\n');
                } catch (URISyntaxException | IOException e) {
                    logger.log(Level.FINEST, e.getMessage(), e);
                }
            }
            ServerResponse.write(serverResponse, HttpResponseStatus.OK, contentType, CharBuffer.wrap(sb), StandardCharsets.ISO_8859_1);
        }
    }

    private static boolean matches(String matchHeader, String toMatch) {
        String[] matchValues = matchHeader.split("\\s*,\\s*");
        Arrays.sort(matchValues);
        return Arrays.binarySearch(matchValues, toMatch) > -1 || Arrays.binarySearch(matchValues, "*") > -1;
    }

    private static long sublong(String value, int beginIndex, int endIndex) {
        String substring = value.substring(beginIndex, endIndex);
        return substring.length() > 0 ? Long.parseLong(substring) : -1;
    }

    private void send(URL url, String contentType,
                        ServerRequest serverRequest, ServerResponse serverResponse) {
        if (url == null) {
            ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
        } else if (serverRequest.getMethod() == HttpMethod.HEAD) {
            ServerResponse.write(serverResponse, HttpResponseStatus.OK, contentType);
        } else {
            if ("file".equals(url.getProtocol())) {
                try {
                    send((FileChannel) Files.newByteChannel(Paths.get(url.toURI())),
                            HttpResponseStatus.OK, contentType, serverResponse);
                } catch (URISyntaxException | IOException e) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                    ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
                }
            } else {
                try (InputStream inputStream = url.openStream()) {
                    send(inputStream, HttpResponseStatus.OK, contentType, serverResponse);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                    ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
                }
            }
        }
    }

    private void send(URL url, HttpResponseStatus httpResponseStatus, String contentType,
                        ServerRequest serverRequest, ServerResponse serverResponse, long offset, long size) {
        if (url == null) {
            ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
        } else if (serverRequest.getMethod() == HttpMethod.HEAD) {
            ServerResponse.write(serverResponse, HttpResponseStatus.OK, contentType);
        } else {
            if ("file".equals(url.getProtocol())) {
                Path path = null;
                try {
                    path = Paths.get(url.toURI());
                    send((FileChannel) Files.newByteChannel(path), httpResponseStatus,
                            contentType, serverResponse, offset, size);
                } catch (URISyntaxException | IOException e) {
                    logger.log(Level.SEVERE, e.getMessage() + " path=" + path, e);
                    ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
                }
            } else {
                try (InputStream inputStream = url.openStream()) {
                    send(inputStream, httpResponseStatus, contentType, serverResponse, offset, size);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                    ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
                }
            }
        }
    }

    private void send(FileChannel fileChannel, HttpResponseStatus httpResponseStatus, String contentType,
                      ServerResponse serverResponse) throws IOException {
        send(fileChannel, httpResponseStatus, contentType, serverResponse, 0L, fileChannel.size());
    }

    private void send(FileChannel fileChannel, HttpResponseStatus httpResponseStatus, String contentType,
                      ServerResponse serverResponse, long offset, long size) {
        if (fileChannel == null) {
            ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
        } else {
            MappedByteBuffer mappedByteBuffer = null;
            try {
                mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, offset, size);
            } catch (IOException e) {
                // resource is not a file that can be mapped
                ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
            }
            if (mappedByteBuffer != null) {
                serverResponse.withStatus(httpResponseStatus)
                        .withContentType(contentType)
                        .write(Unpooled.wrappedBuffer(mappedByteBuffer));
            }
        }
    }

    private void send(InputStream inputStream, HttpResponseStatus httpResponseStatus, String contentType,
                      ServerResponse serverResponse) throws IOException {
        if (inputStream == null) {
            ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
        } else {
            try (ReadableByteChannel channel = Channels.newChannel(inputStream)) {
                serverResponse.withStatus(httpResponseStatus)
                        .withContentType(contentType)
                        .write(new ChunkedNioStream(channel));
            }
        }
    }

    private void send(InputStream inputStream, HttpResponseStatus httpResponseStatus, String contentType,
                      ServerResponse serverResponse, long offset, long size) throws IOException {
        if (inputStream == null) {
            ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND);
        } else {
            serverResponse.withStatus(httpResponseStatus)
                    .withContentType(contentType)
                    .write(Unpooled.wrappedBuffer(readBuffer(inputStream, offset, size)));
        }
    }

    private static ByteBuffer readBuffer(URL url, long offset, long size) throws IOException, URISyntaxException {
        if ("file".equals(url.getProtocol())) {
            try (SeekableByteChannel channel = Files.newByteChannel(Paths.get(url.toURI()))) {
                return readBuffer(channel, offset, size);
            }
        } else {
            try (InputStream inputStream = url.openStream()) {
                return readBuffer(inputStream, offset, size);
            }
        }
    }

    private static ByteBuffer readBuffer(InputStream inputStream, long offset, long size) throws IOException {
        long n = inputStream.skip(offset);
        return readBuffer(Channels.newChannel(inputStream), size);
    }

    private static ByteBuffer readBuffer(SeekableByteChannel channel, long offset, long size) throws IOException {
        channel.position(offset);
        return readBuffer(channel, size);
    }

    private static ByteBuffer readBuffer(ReadableByteChannel channel, long size) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate((int) size);
        buf.rewind();
        channel.read(buf);
        buf.flip();
        return buf;
    }

    class Range {
        long start;
        long end;
        long length;
        long total;

        Range(long start, long end, long total) {
            this.start = start;
            this.end = end;
            this.length = end - start + 1;
            this.total = total;
        }
    }
}
