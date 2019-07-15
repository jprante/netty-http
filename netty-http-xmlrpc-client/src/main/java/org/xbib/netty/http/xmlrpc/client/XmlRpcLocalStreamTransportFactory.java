package org.xbib.netty.http.xmlrpc.client;

import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamRequestProcessor;

/**
 * Another local transport factory for debugging and testing. This one is
 * similar to the {@link XmlRpcLocalTransportFactory},
 * except that it adds request serialization. In other words, it is
 * particularly well suited for development and testing of XML serialization
 * and parsing.
 */
public class XmlRpcLocalStreamTransportFactory extends XmlRpcStreamTransportFactory {

    private final XmlRpcStreamRequestProcessor server;

    /** Creates a new instance.
     * @param pClient The client controlling the factory.
     * @param pServer An instance of {@link XmlRpcStreamRequestProcessor}.
     */
    public XmlRpcLocalStreamTransportFactory(XmlRpcClient pClient,
                                             XmlRpcStreamRequestProcessor pServer) {
        super(pClient);
        server = pServer;
    }

    public XmlRpcTransport getTransport() {
        return new XmlRpcLocalStreamTransport(getClient(), server);
    }
}
