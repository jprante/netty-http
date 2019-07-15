package org.xbib.netty.http.xmlrpc.client;

import org.xbib.netty.http.xmlrpc.common.XmlRpcRequestProcessorFactory;

/**
 * Interface of a client configuration for local rpc calls. Local
 * rpc calls are mainly useful for testing, because you don't need
 * a running server.
 */
public interface XmlRpcLocalClientConfig extends XmlRpcClientConfig,
		XmlRpcRequestProcessorFactory {
}
