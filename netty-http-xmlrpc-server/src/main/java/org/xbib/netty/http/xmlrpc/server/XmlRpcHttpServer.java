package org.xbib.netty.http.xmlrpc.server;

import org.xbib.netty.http.xmlrpc.common.ServerStreamConnection;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamRequestConfig;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Abstract extension of {@link XmlRpcStreamServer} for deriving
 * HTTP servers.
 */
public abstract class XmlRpcHttpServer extends XmlRpcStreamServer {
    protected abstract void setResponseHeader(ServerStreamConnection pConnection, String pHeader, String pValue);

    protected OutputStream getOutputStream(ServerStreamConnection pConnection, XmlRpcStreamRequestConfig pConfig, OutputStream pStream) throws IOException {
        if (pConfig.isEnabledForExtensions()  &&  pConfig.isGzipRequesting()) {
            setResponseHeader(pConnection, "Content-Encoding", "gzip");
        }
        return super.getOutputStream(pConnection, pConfig, pStream);
    }
}
