
package org.xbib.netty.http.server.handler.stream;

import io.netty.handler.stream.ChunkedNioStream;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;

/**
 * A {@link ChunkedNioStream} that fetches data from a {@link SeekableByteChannel}
 * chunk by chunk.  Please note that the {@link SeekableByteChannel} must
 * operate in blocking mode.  Non-blocking mode channels are not supported.
 */
public class SeekableChunkedNioStream extends ChunkedNioStream {

    /**
     * Creates a new instance that fetches data from the specified channel.
     */
    public SeekableChunkedNioStream(SeekableByteChannel in) {
        super(in);
    }

    /**
     * Creates a new instance that fetches data from the specified channel.
     *
     * @param chunkSize the number of bytes to fetch on each call
     */
    public SeekableChunkedNioStream(SeekableByteChannel in, int chunkSize) {
        super(in, chunkSize);
    }

    /**
     * Creates a new instance that fetches data from the specified channel.
     *
     * @param position the position in the byte channel
     * @param chunkSize the number of bytes to fetch on each call
     */
    public SeekableChunkedNioStream(SeekableByteChannel in, long position, int chunkSize) throws IOException {
        super(in, chunkSize);
        in.position(position);
    }
}
