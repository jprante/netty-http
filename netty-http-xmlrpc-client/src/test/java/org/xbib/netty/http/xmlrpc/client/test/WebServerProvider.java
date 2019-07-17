package org.xbib.netty.http.xmlrpc.client.test;

import org.xbib.netty.http.xmlrpc.client.XmlRpcClientConfigImpl;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHandlerMapping;
import org.xbib.netty.http.xmlrpc.server.XmlRpcServer;
import org.xbib.netty.http.xmlrpc.server.XmlRpcServerConfigImpl;
import org.xbib.netty.http.xmlrpc.servlet.WebServer;

import java.io.IOException;
import java.net.URL;

/** Abstract base class for providers, which require a webserver.
 */
public abstract class WebServerProvider extends ClientProviderImpl {

    private WebServer webServer;

    private final boolean contentLength;

    /** Creates a new instance.
     * @param pMapping The test servers handler mapping.
     */
    WebServerProvider(XmlRpcHandlerMapping pMapping, boolean pContentLength) {
        super(pMapping);
        contentLength = pContentLength;
    }

    public final XmlRpcClientConfigImpl getConfig() throws Exception {
        initWebServer();
        return getConfig(new URL("http://localhost:" + webServer.getPort() + "/"));
    }

    protected XmlRpcClientConfigImpl getConfig(URL pServerURL) throws Exception {
        XmlRpcClientConfigImpl config = super.getConfig();
        config.setServerURL(pServerURL);
        config.setContentLengthOptional(!contentLength);
        return config;
    }

    private void initWebServer() throws Exception {
        if (webServer == null || webServer.isShutDown()) {
            webServer = new WebServer(0);
            XmlRpcServer server = webServer.getXmlRpcServer();
            server.setHandlerMapping(mapping);
            XmlRpcServerConfigImpl serverConfig = (XmlRpcServerConfigImpl) server.getConfig();
            serverConfig.setEnabledForExtensions(true);
            serverConfig.setContentLengthOptional(!contentLength);
            serverConfig.setEnabledForExceptions(true);
            webServer.start();
        }
    }

    @Override
    public XmlRpcServer getServer() {
        return webServer.getXmlRpcServer();
    }

    @Override
    public void shutdown() throws IOException {
        webServer.shutdown();
    }
}
