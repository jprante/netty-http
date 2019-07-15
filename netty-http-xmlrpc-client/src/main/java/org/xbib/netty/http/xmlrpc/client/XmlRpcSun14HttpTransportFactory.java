package org.xbib.netty.http.xmlrpc.client;

import javax.net.ssl.SSLSocketFactory;

/**
 * Default implementation of an HTTP transport factory in Java 1.4, based
 * on the {@link java.net.HttpURLConnection} class.
 */
public class XmlRpcSun14HttpTransportFactory extends XmlRpcTransportFactoryImpl {
    private SSLSocketFactory sslSocketFactory;

    /**
     * Creates a new factory, which creates transports for the given client.
     * @param pClient The client, which is operating the factory.
     */
    public XmlRpcSun14HttpTransportFactory(XmlRpcClient pClient) {
        super(pClient);
    }

    /**
     * Sets the SSLSocketFactory to be used by transports.
     * @param pSocketFactory The SSLSocketFactory to use.
     */
    public void setSSLSocketFactory(SSLSocketFactory pSocketFactory) {
        sslSocketFactory = pSocketFactory;
    }

    /**
     * Returns the SSLSocketFactory to be used by transports.
     */
    public SSLSocketFactory getSSLSocketFactory() {
        return sslSocketFactory;
    }

    public XmlRpcTransport getTransport() {
        XmlRpcSun14HttpTransport transport = new XmlRpcSun14HttpTransport(getClient());
        transport.setSSLSocketFactory(sslSocketFactory);
        return transport;
    }
}
