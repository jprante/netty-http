package org.xbib.netty.http.xmlrpc.server;

import org.xbib.netty.http.xmlrpc.common.XmlRpcConfigImpl;

/**
 * Default implementation of {@link XmlRpcServerConfig}.
 */
public class XmlRpcServerConfigImpl extends XmlRpcConfigImpl
		implements XmlRpcServerConfig, XmlRpcHttpServerConfig {
	private boolean isKeepAliveEnabled;
    private boolean isEnabledForExceptions;

	/** Sets, whether HTTP keepalive is enabled for this server.
	 * @param pKeepAliveEnabled True, if keepalive is enabled. False otherwise.
	 */
	public void setKeepAliveEnabled(boolean pKeepAliveEnabled) {
		isKeepAliveEnabled = pKeepAliveEnabled;
	}

	public boolean isKeepAliveEnabled() { return isKeepAliveEnabled; }

    /** Sets, whether the server may create a "faultCause" element in an error
     * response. Note, that this may be a security issue!
     */
    public void setEnabledForExceptions(boolean pEnabledForExceptions) {
        isEnabledForExceptions = pEnabledForExceptions;
    }

    public boolean isEnabledForExceptions() { return isEnabledForExceptions; }
}
