package org.xbib.netty.http.xmlrpc.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Interface of an object, which is able to provide
 * an XML stream, containing an XML-RPC request.
 * Additionally, the object may also be used to
 * write the response as an XML stream.
 */
public interface ServerStreamConnection {

    /**
     * Returns the connection input stream.
     * @return input stream
     * @throws IOException if connection fails
     */
    InputStream newInputStream() throws IOException;

    /**
     * Returns the connection output stream.
     * @return output stream
     * @throws IOException if connection fails
     */
    OutputStream newOutputStream() throws IOException;

    /**
     * Closes the connection, and frees resources.
     * @throws IOException if close fails
     */
    void close() throws IOException;
}
