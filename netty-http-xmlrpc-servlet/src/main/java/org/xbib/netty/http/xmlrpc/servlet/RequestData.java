package org.xbib.netty.http.xmlrpc.servlet;

import org.xbib.netty.http.xmlrpc.common.XmlRpcHttpRequestConfigImpl;

/** Web servers extension of
 * {@link XmlRpcHttpRequestConfig},
 * which allows to store additional per request data.
 */
public class RequestData extends XmlRpcHttpRequestConfigImpl {
	private final Connection connection;
	private boolean keepAlive;
	private String method, httpVersion;
	private int contentLength = -1;
	private boolean success;

	/** Creates a new instance.
	 * @param pConnection The connection, which is serving the request.
	 */
	public RequestData(Connection pConnection) {
		connection = pConnection;
	}

	/** Returns the connection, which is serving the request.
	 * @return The request connection.
	 */
	public Connection getConnection() { return connection; }

	/** Returns, whether HTTP keepAlive is enabled for this
	 * connection.
	 * @return True, if keepAlive is enabled, false otherwise.
	 */
	public boolean isKeepAlive() { return keepAlive; }

	/** Sets, whether HTTP keepAlive is enabled for this
	 * connection.
	 * @param pKeepAlive True, if keepAlive is enabled, false otherwise.
	 */
	public void setKeepAlive(boolean pKeepAlive) {
		keepAlive = pKeepAlive;
	}

	/** Returns the requests HTTP version.
	 * @return HTTP version, for example "1.0"
	 */
	public String getHttpVersion() { return httpVersion; }

	/** Sets the requests HTTP version.
	 * @param pHttpVersion HTTP version, for example "1.0"
	 */
	public void setHttpVersion(String pHttpVersion) {
		httpVersion = pHttpVersion;
	}

	/** Returns the requests content length.
	 * @return Content length, if known, or -1, if unknown.
	 */
	public int getContentLength() { return contentLength; }

	/** Sets the requests content length.
	 * @param pContentLength Content length, if known, or -1, if unknown.
	 */
	public void setContentLength(int pContentLength) {
		contentLength = pContentLength;
	}

	/** Returns, whether a byte array for buffering the output is
	 * required.
	 * @return True, if the byte array is required, false otherwise.
	 */
	public boolean isByteArrayRequired() {
		return isKeepAlive() || !isEnabledForExtensions() || !isContentLengthOptional();
	}

	/** Returns the request method.
	 * @return The request method, should be "POST".
	 */
	public String getMethod() { return method; }

	/** Sets the request method.
	 * @param pMethod The request method, should be "POST".
	 */
	public void setMethod(String pMethod) {
		method = pMethod;
	}

	/** Returns, whether the request was executed successfull.
	 * @return True for success, false, if an error occurred.
	 */
	public boolean isSuccess() { return success; }

	/** Sets, whether the request was executed successfull.
	 * @param pSuccess True for success, false, if an error occurred.
	 */
	public void setSuccess(boolean pSuccess) {
		success = pSuccess;
	}
}
