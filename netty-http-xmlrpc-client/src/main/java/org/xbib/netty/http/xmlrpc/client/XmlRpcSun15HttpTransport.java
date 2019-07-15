package org.xbib.netty.http.xmlrpc.client;

import org.xbib.netty.http.xmlrpc.common.XmlRpcRequest;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/**
 * Default implementation of an HTTP transport in Java 1.4, based on the
 * {@link java.net.HttpURLConnection} class. Adds support for the
 * {@link Proxy} class.
 */
public class XmlRpcSun15HttpTransport extends XmlRpcSun14HttpTransport {
    /**
     * Creates a new instance.
     * @param pClient The client controlling this instance.
     */
    public XmlRpcSun15HttpTransport(XmlRpcClient pClient) {
        super(pClient);
    }

    private Proxy proxy;

    /**
     * Sets the proxy to use.
     */
    public void setProxy(Proxy pProxy) {
        proxy = pProxy;
    }

    /**
     * Returns the proxy to use.
     */
    public Proxy getProxy() {
        return proxy;
    }

    protected void initHttpHeaders(XmlRpcRequest pRequest)
            throws XmlRpcClientException {
        final XmlRpcHttpClientConfig config = (XmlRpcHttpClientConfig) pRequest.getConfig();
        int connectionTimeout = config.getConnectionTimeout();
        if (connectionTimeout > 0) {
            getURLConnection().setConnectTimeout(connectionTimeout);
        }
        int replyTimeout = config.getReplyTimeout();
        if (replyTimeout > 0) {
            getURLConnection().setReadTimeout(replyTimeout);
        }
        super.initHttpHeaders(pRequest);
    }

    protected URLConnection newURLConnection(URL pURL) throws IOException {
        final Proxy prox = getProxy();
        final URLConnection conn = prox == null ? pURL.openConnection() : pURL.openConnection(prox);
        final SSLSocketFactory sslSockFactory = getSSLSocketFactory();
        if (sslSockFactory != null  &&  conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection)conn).setSSLSocketFactory(sslSockFactory);
        }
        return conn;
    }
}
