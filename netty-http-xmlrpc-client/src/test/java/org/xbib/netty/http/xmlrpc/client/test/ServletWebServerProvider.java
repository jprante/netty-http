package org.xbib.netty.http.xmlrpc.client.test;

import org.xbib.netty.http.xmlrpc.client.XmlRpcClient;
import org.xbib.netty.http.xmlrpc.client.XmlRpcClientConfigImpl;
import org.xbib.netty.http.xmlrpc.client.XmlRpcSunHttpTransportFactory;
import org.xbib.netty.http.xmlrpc.client.XmlRpcTransportFactory;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHandlerMapping;
import org.xbib.netty.http.xmlrpc.server.XmlRpcServer;
import org.xbib.netty.http.xmlrpc.server.XmlRpcServerConfigImpl;
import org.xbib.netty.http.xmlrpc.servlet.ServletWebServer;
import org.xbib.netty.http.xmlrpc.servlet.XmlRpcServlet;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URL;

/**
 * A provider class for testing the {@link ServletWebServer}.
 */
public class ServletWebServerProvider extends ClientProviderImpl {

	protected final ServletWebServer webServer;

	protected final XmlRpcServlet servlet;

	private final boolean contentLength;

	private int port;

	/**
	 * Creates a new instance of {@link XmlRpcServlet}.
	 */
	protected XmlRpcServlet newXmlRpcServlet() {
	    return new XmlRpcServlet();
    }
	
	/** Creates a new instance.
	 * @param pMapping The test servers handler mapping.
	 * @throws ServletException 
	 * @throws IOException 
	 */
	protected ServletWebServerProvider(XmlRpcHandlerMapping pMapping, boolean pContentLength) throws ServletException, IOException {
		super(pMapping);
		contentLength = pContentLength;
		servlet = newXmlRpcServlet();
		webServer = new ServletWebServer(servlet, 0);
		try {
			XmlRpcServer server = servlet.getXmlRpcServletServer();
			server.setHandlerMapping(mapping);
			XmlRpcServerConfigImpl serverConfig = (XmlRpcServerConfigImpl) server.getConfig();
			serverConfig.setEnabledForExtensions(true);
			serverConfig.setContentLengthOptional(!contentLength);
			serverConfig.setEnabledForExceptions(true);
			webServer.start();
			port = webServer.getPort();
		} catch (Exception e) {
			webServer.shutdown();
		}
	 }

	public final XmlRpcClientConfigImpl getConfig() throws Exception {
		return getConfig(new URL("http://localhost:" + port + "/"));
	}

	protected XmlRpcClientConfigImpl getConfig(URL pServerURL) throws Exception {
		XmlRpcClientConfigImpl config = super.getConfig();
		config.setServerURL(pServerURL);
		config.setContentLengthOptional(!contentLength);
		return config;
	}

	protected XmlRpcTransportFactory getTransportFactory(XmlRpcClient pClient) {
		return new XmlRpcSunHttpTransportFactory(pClient);
	}

    public XmlRpcServer getServer() {
        return servlet.getXmlRpcServletServer();
    }

    public void shutdown() throws IOException {
        webServer.shutdown();
    }
}
