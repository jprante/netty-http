package org.xbib.netty.http.xmlrpc.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Implementation of {@link ServerStreamConnection}
 */
public class LocalStreamConnection {
    private class LocalServerStreamConnection implements ServerStreamConnection {
        public InputStream newInputStream() throws IOException {
            return request;
        }

        public OutputStream newOutputStream() throws IOException {
            return response;
        }

        public void close() throws IOException {
			response.close();
		}
    }

    private final InputStream request;
	private final XmlRpcStreamRequestConfig config;
	private final ByteArrayOutputStream response = new ByteArrayOutputStream();
    private final ServerStreamConnection serverStreamConnection;

	/**
	 * Creates a new instance with the given request stream.
	 * @param pConfig config
	 * @param pRequest request
	 */
	public LocalStreamConnection(XmlRpcStreamRequestConfig pConfig, 
			InputStream pRequest) {
		config = pConfig;
		request = pRequest;
        serverStreamConnection = new LocalServerStreamConnection();
	}

	/**
	 * Returns the request stream.
	 * @return stream
	 */
	public InputStream getRequest() {
		return request;
	}

	/**
	 * Returns the request configuration.
	 * @return config
	 */
	public XmlRpcStreamRequestConfig getConfig() {
		return config;
	}

	/**
	 * Returns an output stream, to which the response
	 * may be written.
	 * @return response
	 */
	public ByteArrayOutputStream getResponse() {
		return response;
	}

    /**
	 * Returns the server connection.
	 * @return server connection
     */
    public ServerStreamConnection getServerStreamConnection() {
        return serverStreamConnection;
    }
}
