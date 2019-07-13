package org.xbib.netty.http.xmlrpc.common;

/**
 * Interface of a request configuration. Depending on
 * the transport, implementations will also implement
 * additional interfaces like
 * {@link XmlRpcStreamRequestConfig}.
 */
public interface XmlRpcRequestConfig extends XmlRpcConfig {
}
