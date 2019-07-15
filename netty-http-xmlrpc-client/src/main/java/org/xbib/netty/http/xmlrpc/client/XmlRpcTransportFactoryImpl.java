package org.xbib.netty.http.xmlrpc.client;

/**
 * Abstract base implementation of an {@link XmlRpcTransportFactory}.
 */
public abstract class XmlRpcTransportFactoryImpl implements XmlRpcTransportFactory {
    private final XmlRpcClient client;

    /** Creates a new instance.
     * @param pClient The client, which will invoke the factory.
     */
    protected XmlRpcTransportFactoryImpl(XmlRpcClient pClient) {
        client = pClient;
    }

    /** Returns the client operating this factory.
     * @return The client.
     */
    public XmlRpcClient getClient() { return client; }
}
