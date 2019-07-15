package org.xbib.netty.http.xmlrpc.client;

/**
 * <p>A transport factory being used for local XML-RPC calls. Local XML-RPC
 * calls are mainly useful for development and unit testing: Both client
 * and server are runing within the same JVM and communication is implemented
 * in simple method invokcations.</p>
 * <p>This class is thread safe and the returned instance of
 * {@link XmlRpcTransport} will always return the
 * same object, an instance of {@link XmlRpcLocalTransport}</p>
 */
public class XmlRpcLocalTransportFactory extends XmlRpcTransportFactoryImpl {

    /**
     * Creates a new instance, operated by the given client.
     * @param pClient The client, which will invoke the factory.
     */
    public XmlRpcLocalTransportFactory(XmlRpcClient pClient) {
        super(pClient);
    }

    private final XmlRpcTransport LOCAL_TRANSPORT = new XmlRpcLocalTransport(getClient());

    public XmlRpcTransport getTransport() { return LOCAL_TRANSPORT; }
}
