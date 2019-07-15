package org.xbib.netty.http.xmlrpc.servlet;

import org.xbib.netty.http.xmlrpc.common.ServerStreamConnection;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamRequestConfig;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

class ConnectionServer extends XmlRpcHttpServer {
	protected void writeError(XmlRpcStreamRequestConfig pConfig, OutputStream pStream,
							  Throwable pError) throws XmlRpcException {
		RequestData data = (RequestData) pConfig;
		try {
			if (data.isByteArrayRequired()) {
				super.writeError(pConfig, pStream, pError);
				data.getConnection().writeError(data, pError, (ByteArrayOutputStream) pStream);
			} else {
				data.getConnection().writeErrorHeader(data, pError, -1);
				super.writeError(pConfig, pStream, pError);
				pStream.flush();
			}
		} catch (IOException e) {
			throw new XmlRpcException(e.getMessage(), e);
		}
	}

	protected void writeResponse(XmlRpcStreamRequestConfig pConfig, OutputStream pStream, Object pResult) throws XmlRpcException {
		RequestData data = (RequestData) pConfig;
		try {
			if (data.isByteArrayRequired()) {
				super.writeResponse(pConfig, pStream, pResult);
				data.getConnection().writeResponse(data, pStream);
			} else {
				data.getConnection().writeResponseHeader(data, -1);
				super.writeResponse(pConfig, pStream, pResult);
				pStream.flush();
			}
		} catch (IOException e) {
			throw new XmlRpcException(e.getMessage(), e);
		}
	}

	protected void setResponseHeader(ServerStreamConnection pConnection, String pHeader, String pValue) {
		((Connection) pConnection).setResponseHeader(pHeader, new String[] { pValue });
	}
}