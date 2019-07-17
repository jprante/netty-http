package org.xbib.netty.http.xmlrpc.client.test;

import java.io.IOException;
import java.io.StringWriter;

import junit.framework.TestCase;
import org.xbib.netty.http.xmlrpc.client.XmlRpcClient;
import org.xbib.netty.http.xmlrpc.client.XmlRpcClientConfig;
import org.xbib.netty.http.xmlrpc.client.XmlRpcClientConfigImpl;
import org.xbib.netty.http.xmlrpc.common.TypeConverterFactory;
import org.xbib.netty.http.xmlrpc.common.TypeConverterFactoryImpl;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequest;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamConfig;
import org.xbib.netty.http.xmlrpc.common.serializer.XmlRpcWriter;
import org.xbib.netty.http.xmlrpc.common.util.XMLWriter;
import org.xbib.netty.http.xmlrpc.common.util.XMLWriterImpl;
import org.xbib.netty.http.xmlrpc.server.PropertyHandlerMapping;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHandlerMapping;
import org.xml.sax.SAXException;

import javax.servlet.ServletException;

/**
 * Abstract base class for deriving test cases.
 */
public abstract class XmlRpcTestCase extends TestCase {

    protected ClientProvider[] providers;

    protected abstract XmlRpcHandlerMapping getHandlerMapping() throws IOException, XmlRpcException;

    protected XmlRpcClientConfigImpl getConfig(ClientProvider pProvider) throws Exception {
        return pProvider.getConfig();
    }

    XmlRpcClientConfig getExConfig(ClientProvider pProvider) throws Exception {
        XmlRpcClientConfigImpl config = getConfig(pProvider);
        config.setEnabledForExtensions(true);
        config.setEnabledForExceptions(true);
        return config;
    }

    XmlRpcHandlerMapping getHandlerMapping(String pResource) throws IOException, XmlRpcException {
        PropertyHandlerMapping mapping = new PropertyHandlerMapping();
        mapping.setVoidMethodEnabled(true);
        mapping.load(getClass().getClassLoader(), getClass().getResource(pResource));
        mapping.setTypeConverterFactory(getTypeConverterFactory());
        return mapping;
    }

    protected ClientProvider[] initProviders(XmlRpcHandlerMapping pMapping) throws ServletException, IOException {
        return new ClientProvider[]{
                new LocalTransportProvider(pMapping),
                //new LocalStreamTransportProvider(pMapping),
                //new LiteTransportProvider(pMapping, true),
                //// new LiteTransportProvider(mapping, false), Doesn't support HTTP/1.1
                //new SunHttpTransportProvider(pMapping, true),
                //new SunHttpTransportProvider(pMapping, false),
                //new CommonsProvider(pMapping),
                //new ServletWebServerProvider(pMapping, true),
                //new ServletWebServerProvider(pMapping, false)
            };
    }

    @Override
    public void setUp() throws Exception {
        if (providers == null) {
            providers = initProviders(getHandlerMapping());
        }
    }

    @Override
    public void tearDown() throws IOException {
        if (providers != null) {
            for (ClientProvider provider : providers) {
                provider.shutdown();
            }
        }
    }

    protected TypeConverterFactory getTypeConverterFactory() {
        return new TypeConverterFactoryImpl();
    }

    static String writeRequest(XmlRpcClient pClient, XmlRpcRequest pRequest)
            throws SAXException {
        StringWriter sw = new StringWriter();
        XMLWriter xw = new XMLWriterImpl();
        xw.setEncoding("US-ASCII");
        xw.setDeclarating(true);
        xw.setIndenting(false);
        xw.setWriter(sw);
        XmlRpcWriter xrw = new XmlRpcWriter((XmlRpcStreamConfig) pClient.getConfig(), xw, pClient.getTypeFactory());
        xrw.write(pRequest);
        return sw.toString();
    }
}
