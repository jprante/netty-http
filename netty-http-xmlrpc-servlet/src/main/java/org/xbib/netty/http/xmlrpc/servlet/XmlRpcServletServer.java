package org.xbib.netty.http.xmlrpc.servlet;

import org.xbib.netty.http.xmlrpc.common.ServerStreamConnection;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcHttpRequestConfig;
import org.xbib.netty.http.xmlrpc.common.XmlRpcHttpRequestConfigImpl;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamRequestConfig;
import org.xbib.netty.http.xmlrpc.common.util.HttpUtil;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHttpServer;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHttpServerConfig;
import org.xbib.netty.http.xmlrpc.server.XmlRpcServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** An extension of {@link XmlRpcServer},
 * which is suitable for processing servlet requests.
 */
public class XmlRpcServletServer extends XmlRpcHttpServer {
	protected static class ServletStreamConnection implements ServerStreamConnection {
		private final HttpServletRequest request;
		private final HttpServletResponse response;

		protected ServletStreamConnection(HttpServletRequest pRequest,
				HttpServletResponse pResponse) {
			request = pRequest;
			response = pResponse;
		}

		/** Returns the servlet request.
		 */
		public HttpServletRequest getRequest() { return request; }
		/** Returns the servlet response.
		 */
		public HttpServletResponse getResponse() { return response; }

		public InputStream newInputStream() throws IOException {
            return request.getInputStream();
        }

		public OutputStream newOutputStream() throws IOException {
            response.setContentType("text/xml");
            return response.getOutputStream();
        }

        public void close() throws IOException {
            response.getOutputStream().close();
        }
    }

	/**
	 * @param pRequest The request object.
	 */
	protected XmlRpcHttpRequestConfigImpl newConfig(HttpServletRequest pRequest) {
		return new XmlRpcHttpRequestConfigImpl();
	}

	protected XmlRpcHttpRequestConfigImpl getConfig(HttpServletRequest pRequest) {
		XmlRpcHttpRequestConfigImpl result = newConfig(pRequest);
		XmlRpcHttpServerConfig serverConfig = (XmlRpcHttpServerConfig) getConfig();
		result.setBasicEncoding(serverConfig.getBasicEncoding());
		result.setContentLengthOptional(serverConfig.isContentLengthOptional()
		        && (pRequest.getHeader("Content-Length") == null));
		result.setEnabledForExtensions(serverConfig.isEnabledForExtensions());
		result.setGzipCompressing(HttpUtil.isUsingGzipEncoding(pRequest.getHeader("Content-Encoding")));
		result.setGzipRequesting(HttpUtil.isUsingGzipEncoding(pRequest.getHeaders("Accept-Encoding")));
		result.setEncoding(pRequest.getCharacterEncoding());
        result.setEnabledForExceptions(serverConfig.isEnabledForExceptions());
		HttpUtil.parseAuthorization(result, pRequest.getHeader("Authorization"));
		return result;
	}

	protected ServletStreamConnection newStreamConnection(HttpServletRequest pRequest,
			HttpServletResponse pResponse) {
		return new ServletStreamConnection(pRequest, pResponse);
	}

	/** Processes the servlet request.
	 * @param pRequest The servlet request being read.
	 * @param pResponse The servlet response being created.
	 * @throws IOException Reading the request or writing the response failed.
	 * @throws ServletException Processing the request failed.
	 */
	public void execute(HttpServletRequest pRequest, HttpServletResponse pResponse)
			throws ServletException, IOException {
		XmlRpcHttpRequestConfigImpl config = getConfig(pRequest);
		ServletStreamConnection ssc = newStreamConnection(pRequest, pResponse);
		try {
			super.execute(config, ssc);
		} catch (XmlRpcException e) {
			throw new ServletException(e);
		}
	}

	/** Returns, whether the requests content length is required.
	 */
	protected boolean isContentLengthRequired(XmlRpcStreamRequestConfig pConfig) {
		if (!pConfig.isEnabledForExtensions()) {
			// The spec requires a content-length.
			return true;
		}
		boolean isRequired = !((XmlRpcHttpServerConfig) getConfig()).isContentLengthOptional();
		if(pConfig instanceof XmlRpcHttpRequestConfig) {
		    isRequired |= !((XmlRpcHttpRequestConfig)pConfig).isContentLengthOptional();
		}
		return isRequired;
	}

	protected OutputStream getOutputStream(XmlRpcStreamRequestConfig pConfig,
										   ServerStreamConnection pConnection,
										   int pSize) throws IOException {
		if (pSize != -1) {
			((ServletStreamConnection) pConnection).getResponse().setContentLength(pSize);
		}
		return super.getOutputStream(pConfig, pConnection, pSize);
	}

	protected void setResponseHeader(ServerStreamConnection pConnection, String pHeader, String pValue) {
		((ServletStreamConnection) pConnection).getResponse().setHeader(pHeader, pValue);
	}
}
