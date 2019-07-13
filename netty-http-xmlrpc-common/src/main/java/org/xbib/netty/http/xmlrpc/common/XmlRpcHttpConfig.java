package org.xbib.netty.http.xmlrpc.common;

/**
 * Interface of a configuration for HTTP requests.
 */
public interface XmlRpcHttpConfig extends XmlRpcStreamConfig {

    /**
     * Returns the encoding being used to convert the String "username:password"
     * into bytes.
     * @return Encoding being used for basic HTTP authentication credentials,
     * or null, if the default encoding
     * ({@link XmlRpcStreamRequestConfig#UTF8_ENCODING})
     * is being used.
     */
    String getBasicEncoding();

    /** Returns, whether a "Content-Length" header may be
     * omitted. The XML-RPC specification demands, that such
     * a header be present.
     * @return True, if the content length may be omitted.
     */
    boolean isContentLengthOptional();
}
