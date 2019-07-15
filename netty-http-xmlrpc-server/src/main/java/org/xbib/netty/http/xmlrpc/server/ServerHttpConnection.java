package org.xbib.netty.http.xmlrpc.server;

import org.xbib.netty.http.xmlrpc.common.ServerStreamConnection;

/** Interface of a {@link ServerStreamConnection} for HTTP
 * response transport.
 */
public interface ServerHttpConnection extends ServerStreamConnection {
    /** Sets a response header.
     */
    void setResponseHeader(String pKey, String pValue);
    /** Sets the content length.
     */
    void setContentLength(int pContentLength);
}
