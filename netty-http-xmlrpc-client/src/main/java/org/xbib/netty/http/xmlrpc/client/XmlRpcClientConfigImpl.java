package org.xbib.netty.http.xmlrpc.client;

import org.xbib.netty.http.xmlrpc.common.XmlRpcHttpRequestConfigImpl;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequestProcessor;

import java.io.Serializable;
import java.net.URL;

/**
 * Default implementation of a clients request configuration.
 */
public class XmlRpcClientConfigImpl extends XmlRpcHttpRequestConfigImpl
        implements XmlRpcHttpClientConfig, XmlRpcLocalClientConfig, Cloneable, Serializable {
    private static final long serialVersionUID = 4121131450507800889L;
    private URL serverURL;
    private XmlRpcRequestProcessor xmlRpcServer;
    private String userAgent;

    /** Creates a new client configuration with default settings.
     */
    public XmlRpcClientConfigImpl() {
    }

    /** Creates a clone of this client configuration.
     * @return A clone of this configuration.
     */
    public XmlRpcClientConfigImpl cloneMe() {
        try {
            return (XmlRpcClientConfigImpl) clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Unable to create my clone");
        }
    }

    /** Sets the servers URL.
     * @param pURL Servers URL
     */
    public void setServerURL(URL pURL) {
        serverURL = pURL;
    }

    public URL getServerURL() { return serverURL; }
    /** Returns the {@link XmlRpcRequestProcessor} being invoked.
     * @param pServer Server object being invoked. This will typically
     * be a singleton instance, but could as well create a new
     * instance with any call.
     */
    public void setXmlRpcServer(XmlRpcRequestProcessor pServer) {
        xmlRpcServer = pServer;
    }

    public XmlRpcRequestProcessor getXmlRpcServer() { return xmlRpcServer; }

    /**
     * Returns the user agent header to use 
     * @return the http user agent header to set when doing xmlrpc requests
     */
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * @param pUserAgent the http user agent header to set when doing xmlrpc requests
     */
    public void setUserAgent(String pUserAgent) {
        userAgent = pUserAgent;
    }
}
