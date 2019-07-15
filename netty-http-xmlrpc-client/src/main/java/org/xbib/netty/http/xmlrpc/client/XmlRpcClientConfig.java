package org.xbib.netty.http.xmlrpc.client;

import org.xbib.netty.http.xmlrpc.common.XmlRpcRequestConfig;

/**
 * This interface is being implemented by an Apache XML-RPC clients
 * configuration object. Depending on the transport factory, a
 * configuration object must implement additional methods. For
 * example, an HTTP transport requires an instance of
 * {@link XmlRpcHttpClientConfig}. A
 * local transport requires an instance of
 * {@link XmlRpcLocalClientConfig}.
 */
public interface XmlRpcClientConfig extends XmlRpcRequestConfig {
}
