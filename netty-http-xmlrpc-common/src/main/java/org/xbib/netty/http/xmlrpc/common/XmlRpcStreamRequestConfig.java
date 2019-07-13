package org.xbib.netty.http.xmlrpc.common;

/**
 * Interface of a client configuration for a transport, which
 * is implemented by writing to a stream.
 */
public interface XmlRpcStreamRequestConfig extends XmlRpcStreamConfig, XmlRpcRequestConfig {

    /**
     * Returns true if the request stream is being compressed. Note,
     * that the response stream may still be uncompressed.
     * @return Whether to use Gzip compression or not. Defaults to false.
     * @see #isGzipRequesting()
     */
    boolean isGzipCompressing();

    /**
     * Returns true if compression is requested for the response stream.
     * Note, that the request is stull uncompressed, unless
     * {@link #isGzipCompressing()} is activated. Also note, that the
     * server may still decide to send uncompressed data.
     * @return Whether to use Gzip compression or not. Defaults to false.
     * @see #isGzipCompressing()
     */
    boolean isGzipRequesting();

    /**
     * Returns true if the response should contain a "faultCause" element
     * in case of errors. The "faultCause" is an exception, which the
     * server has trapped and written into a byte stream as a serializable
     * object.
     * @return true if enabled for exceptions
     */
    boolean isEnabledForExceptions();
}
