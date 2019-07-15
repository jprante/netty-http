package org.xbib.netty.http.xmlrpc.server;

import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequest;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequestProcessor;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequestProcessorFactory;

/**
 * Server part of a local stream transport.
 */
public class XmlRpcLocalStreamServer extends XmlRpcStreamServer {
    @Override
    public Object execute(XmlRpcRequest pRequest) throws XmlRpcException {
        XmlRpcRequestProcessor server = ((XmlRpcRequestProcessorFactory) pRequest.getConfig()).getXmlRpcServer();
        return server.execute(pRequest);
    }
}