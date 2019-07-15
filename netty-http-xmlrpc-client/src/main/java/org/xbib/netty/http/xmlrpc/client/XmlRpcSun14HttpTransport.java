package org.xbib.netty.http.xmlrpc.client;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/**
 * Default implementation of an HTTP transport in Java 1.4, based on the
 * {@link java.net.HttpURLConnection} class. Adds support for the
 * {@link SSLSocketFactory}.
 */
public class XmlRpcSun14HttpTransport extends XmlRpcSunHttpTransport {
    private SSLSocketFactory sslSocketFactory;

    /**
     * Creates a new instance.
     * @param pClient The client controlling this instance.
     */
    public XmlRpcSun14HttpTransport(XmlRpcClient pClient) {
        super(pClient);
    }

    /**
     * Sets the SSLSocketFactory used to create secure sockets.
     * @param pSocketFactory The SSLSocketFactory to use.
     */
    public void setSSLSocketFactory(SSLSocketFactory pSocketFactory) {
        sslSocketFactory = pSocketFactory;
    }

    /**
     * Returns the SSLSocketFactory used to create secure sockets.
     */
    public SSLSocketFactory getSSLSocketFactory() {
        return sslSocketFactory;
    }

    protected URLConnection newURLConnection(URL pURL) throws IOException {
        final URLConnection conn = super.newURLConnection(pURL);
        final SSLSocketFactory sslSockFactory = getSSLSocketFactory();
        if ((sslSockFactory != null) && (conn instanceof HttpsURLConnection))
            ((HttpsURLConnection)conn).setSSLSocketFactory(sslSockFactory);
        return conn;
    }
}
