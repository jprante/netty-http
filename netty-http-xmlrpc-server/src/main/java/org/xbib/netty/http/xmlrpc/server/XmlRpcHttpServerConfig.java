package org.xbib.netty.http.xmlrpc.server;

import org.xbib.netty.http.xmlrpc.common.XmlRpcHttpConfig;

/**
 * HTTP servers configuration.
 */
public interface XmlRpcHttpServerConfig extends XmlRpcServerConfig, XmlRpcHttpConfig {
    /** Returns, whether HTTP keepalive is being enabled.
     * @return True, if keepalive is enabled, false otherwise.
     */
    boolean isKeepAliveEnabled();

    /** Returns, whether the server may create a "faultCause" element in an error
     * response. Note, that this may be a security issue!
     */
    boolean isEnabledForExceptions();
}
