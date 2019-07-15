package org.xbib.netty.http.xmlrpc.client;

import org.xbib.netty.http.xmlrpc.common.XmlRpcHttpRequestConfig;

import java.net.URL;

/** Extension of {@link XmlRpcClientConfig}
 * for HTTP based transport. Provides details like server URL,
 * user credentials, and so on.
 */
public interface XmlRpcHttpClientConfig extends XmlRpcHttpRequestConfig {
	/** Returns the HTTP servers URL.
	 * @return XML-RPC servers URL; for example, this may be the URL of a
	 * servlet
	 */
	URL getServerURL();
    
    /**
     * Returns the user agent header to use 
     * @return the http user agent header to set when doing xmlrpc requests
     */
    String getUserAgent();
}
