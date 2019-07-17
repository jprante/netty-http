package org.xbib.netty.http.xmlrpc.client.test;

import java.io.IOException;
import java.text.DateFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.xbib.netty.http.xmlrpc.client.XmlRpcClient;
import org.xbib.netty.http.xmlrpc.client.XmlRpcClientConfigImpl;
import org.xbib.netty.http.xmlrpc.client.XmlRpcClientRequestImpl;
import org.xbib.netty.http.xmlrpc.common.TypeFactory;
import org.xbib.netty.http.xmlrpc.common.TypeFactoryImpl;
import org.xbib.netty.http.xmlrpc.common.XmlRpcController;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequest;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamConfig;
import org.xbib.netty.http.xmlrpc.common.parser.DateParser;
import org.xbib.netty.http.xmlrpc.common.parser.TypeParser;
import org.xbib.netty.http.xmlrpc.common.serializer.DateSerializer;
import org.xbib.netty.http.xmlrpc.common.serializer.TypeSerializer;
import org.xbib.netty.http.xmlrpc.common.util.NamespaceContextImpl;
import org.xbib.netty.http.xmlrpc.server.PropertyHandlerMapping;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHandlerMapping;
import org.xbib.netty.http.xmlrpc.server.XmlRpcServer;
import org.xml.sax.SAXException;

/**
 * Test suite for working with custom types.
 */
public class CustomTypesTest extends XmlRpcTestCase {
    /**
     * Sample date converter
     */
    public static class DateConverter {
        /**
         * Adds one day to the given date.
         */
        public Date tomorrow(Date pDate) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(pDate);
            cal.add(Calendar.DAY_OF_MONTH, 1);
            return cal.getTime();
        }
    }
    
    protected XmlRpcHandlerMapping getHandlerMapping() throws IOException, XmlRpcException {
        PropertyHandlerMapping mapping = new PropertyHandlerMapping();
        mapping.addHandler("DateConverter", DateConverter.class);
        return mapping;
    }

    /** Tests using a custom date format.
     */
    public void testCustomDateFormat() throws Exception {
        for (int i = 0;  i < providers.length;  i++) {
            testCustomDateFormat(providers[i]);
        }
    }

    private TypeFactory getCustomDateTypeFactory(XmlRpcController pController, final Format pFormat) {
        return new TypeFactoryImpl(pController){
            private TypeSerializer dateSerializer = new DateSerializer(pFormat);

            public TypeParser getParser(XmlRpcStreamConfig pConfig, NamespaceContextImpl pContext, String pURI, String pLocalName) {
                if (DateSerializer.DATE_TAG.equals(pLocalName)) {
                    return new DateParser(pFormat);
                } else {
                    return super.getParser(pConfig, pContext, pURI, pLocalName);
                }
            }

            public TypeSerializer getSerializer(XmlRpcStreamConfig pConfig, Object pObject) throws SAXException {
                if (pObject instanceof Date) {
                    return dateSerializer;
                } else {
                    return super.getSerializer(pConfig, pObject);
                }
            }
            
        };
    }

    private void testCustomDateFormat(ClientProvider pProvider) throws Exception {
        final DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        XmlRpcClient client = pProvider.getClient();
        XmlRpcClientConfigImpl config = getConfig(pProvider);
        client.setConfig(config);
        TypeFactory typeFactory = getCustomDateTypeFactory(client, format);
        client.setTypeFactory(typeFactory);
        Calendar cal1 = Calendar.getInstance();
        XmlRpcRequest request = new XmlRpcClientRequestImpl(config, "DateConverter.tomorrow", new Object[]{cal1.getTime()});
        final String got = XmlRpcTestCase.writeRequest(client, request);
        final String expect = "<?xml version=\"1.0\" encoding=\"US-ASCII\"?>"
            + "<methodCall><methodName>DateConverter.tomorrow</methodName>"
            + "<params><param><value><dateTime.iso8601>" + format.format(cal1.getTime())
            + "</dateTime.iso8601></value></param></params></methodCall>";
        assertEquals(expect, got);
        
        XmlRpcServer server = pProvider.getServer();
        server.setTypeFactory(getCustomDateTypeFactory(server, format));
        Date date = (Date) client.execute(request);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date);
        cal1.add(Calendar.DAY_OF_MONTH, 1);
        assertEquals(cal1, cal2);
    }
}
