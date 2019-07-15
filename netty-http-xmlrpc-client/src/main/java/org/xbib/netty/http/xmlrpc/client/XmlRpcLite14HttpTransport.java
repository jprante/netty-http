package org.xbib.netty.http.xmlrpc.client;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.SSLSocketFactory;

/**
 * A "light" HTTP transport implementation for Java 1.4.
 */
public class XmlRpcLite14HttpTransport extends XmlRpcLiteHttpTransport {
    private SSLSocketFactory sslSocketFactory;

    /**
     * Creates a new instance.
     * @param pClient The client controlling this instance.
     */
    public XmlRpcLite14HttpTransport(XmlRpcClient pClient) {
        super(pClient);
    }

    /**
     * Sets the SSL Socket Factory to use for https connections.
     */
    public SSLSocketFactory getSSLSocketFactory() {
        return sslSocketFactory;
    }

    /**
     * Returns the SSL Socket Factory to use for https connections.
     */
    public void setSSLSocketFactory(SSLSocketFactory pSSLSocketFactory) {
        sslSocketFactory = pSSLSocketFactory;
    }

    protected Socket newSocket(boolean pSSL, String pHostName, int pPort) throws UnknownHostException, IOException {
        if (pSSL) {
            SSLSocketFactory sslSockFactory = getSSLSocketFactory();
            if (sslSockFactory == null) {
                sslSockFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            }
            return sslSockFactory.createSocket(pHostName, pPort);
        } else {
            return super.newSocket(pSSL, pHostName, pPort);
        }
    }
}
