package org.xbib.netty.http.xmlrpc.client;

/** Interface of an object creating instances of
 * {@link XmlRpcTransport}. The implementation
 * is typically based on singletons.
 */
public interface XmlRpcTransportFactory {
    /** Returns an instance of {@link XmlRpcTransport}. This may
     * be a singleton, but the caller should not depend on that:
     * A new instance may as well be created for any request.
     * @return The configured transport.
     */
    public XmlRpcTransport getTransport();
}
