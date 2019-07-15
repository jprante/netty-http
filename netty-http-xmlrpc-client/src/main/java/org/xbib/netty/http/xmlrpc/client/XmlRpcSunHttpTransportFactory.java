package org.xbib.netty.http.xmlrpc.client;

/** Default implementation of a HTTP transport factory, based on the
 * {@link java.net.HttpURLConnection} class.
 */
public class XmlRpcSunHttpTransportFactory extends XmlRpcTransportFactoryImpl {
    /** Creates a new factory, which creates transports for the given client.
	 * @param pClient The client, which is operating the factory.
	 */
	public XmlRpcSunHttpTransportFactory(XmlRpcClient pClient) {
		super(pClient);
	 }

	public XmlRpcTransport getTransport() {
	    return new XmlRpcSunHttpTransport(getClient());
	}
}
