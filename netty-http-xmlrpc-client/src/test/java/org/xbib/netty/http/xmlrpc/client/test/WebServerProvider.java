package org.xbib.netty.http.xmlrpc.client.test;

import org.xbib.netty.http.xmlrpc.client.XmlRpcClientConfigImpl;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHandlerMapping;
import org.xbib.netty.http.xmlrpc.server.XmlRpcServer;
import org.xbib.netty.http.xmlrpc.server.XmlRpcServerConfigImpl;
import org.xbib.netty.http.xmlrpc.servlet.WebServer;

import java.net.URL;

/** Abstract base class for providers, which require a webserver.
 */
public abstract class WebServerProvider extends ClientProviderImpl {
	protected final WebServer webServer = new WebServer(0);
	private boolean isActive;
	private final boolean contentLength;

	/** Creates a new instance.
	 * @param pMapping The test servers handler mapping.
	 */
	protected WebServerProvider(XmlRpcHandlerMapping pMapping, boolean pContentLength) {
		super(pMapping);
		contentLength = pContentLength;
	}

	public final XmlRpcClientConfigImpl getConfig() throws Exception {
		initWebServer();
		return getConfig(new URL("http://127.0.0.1:" + webServer.getPort() + "/"));
	}

	protected XmlRpcClientConfigImpl getConfig(URL pServerURL) throws Exception {
		XmlRpcClientConfigImpl config = super.getConfig();
		config.setServerURL(pServerURL);
		config.setContentLengthOptional(!contentLength);
		return config;
	}

	protected void initWebServer() throws Exception {
		if (!isActive) {
			XmlRpcServer server = webServer.getXmlRpcServer();
			server.setHandlerMapping(mapping);
			XmlRpcServerConfigImpl serverConfig = (XmlRpcServerConfigImpl) server.getConfig();
			serverConfig.setEnabledForExtensions(true);
			serverConfig.setContentLengthOptional(!contentLength);
            serverConfig.setEnabledForExceptions(true);
			webServer.start();
			isActive = true;
		}
	}

	public XmlRpcServer getServer() {
	    return webServer.getXmlRpcServer();
    }

	public void shutdown() {
	    webServer.shutdown();
    }
}
