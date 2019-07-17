package org.xbib.netty.http.xmlrpc.client.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

import org.xbib.netty.http.xmlrpc.client.ClientFactory;
import org.xbib.netty.http.xmlrpc.client.TimingOutCallback;
import org.xbib.netty.http.xmlrpc.client.XmlRpcClient;
import org.xbib.netty.http.xmlrpc.client.XmlRpcHttpClientConfig;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamRequestConfig;
import org.xbib.netty.http.xmlrpc.common.parser.XmlRpcResponseParser;
import org.xbib.netty.http.xmlrpc.common.util.SAXParsers;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHandlerMapping;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;


/**
 * Test case for various jira issues.
 */ 
public class JiraTest extends XmlRpcTestCase {
    /** Interface of the handler for {@link JiraTest#testXMLRPC89()}
     */
    public interface XMLRPC89Handler {
        /**
         * Returns the reversed vector.
         */
        Vector<Object> reverse(Vector<Object> pVector);
        /**
         * Returns the same hashtable, but doubles the
         * values.
         */
        Hashtable<Object, Object> doubledValues(Hashtable<Object, Object> pMap);
        /**
         * Returns the same properties, but doubles the
         * values.
         */
        Properties doubledPropertyValues(Properties pMap);
    }

    /**
     * Handler for {@link JiraTest#testXMLRPC89()}
     */ 
    public static class XMLRPC89HandlerImpl implements XMLRPC89Handler {
        public Vector<Object> reverse(Vector<Object> pVector) {
            Vector<Object> result = new Vector<>(pVector.size());
            result.addAll(pVector);
            Collections.reverse(result);
            return result;
        }
        public Hashtable<Object, Object> doubledValues(Hashtable<Object, Object> pMap) {
            final Hashtable<Object, Object> result;
            if (pMap instanceof Properties) {
                result = new Properties();
            } else {
                result = new Hashtable<>();
            }
            result.putAll(pMap);
            for (Map.Entry<Object, Object> entry : result.entrySet()) {
                Object value = entry.getValue();
                final Integer i;
                if (pMap instanceof Properties) {
                    i = Integer.valueOf((String) value);
                } else {
                    i = (Integer) value;
                }
                Integer iDoubled = i * 2;
                if (pMap instanceof Properties) {
                    entry.setValue(iDoubled.toString());
                } else {
                    entry.setValue(iDoubled);
                }
            }
            return result;
        }
        public Properties doubledPropertyValues(Properties pProperties) {
            return (Properties) doubledValues(pProperties);
        }
    }

    protected XmlRpcHandlerMapping getHandlerMapping() throws IOException,
            XmlRpcException {
        return getHandlerMapping("JiraTest.properties");
    }

    /**
     * Test case for <a href="http://issues.apache.org/jira/browse/XMLRPC-89">
     * XMLRPC-89</a>
     */
    public void testXMLRPC89() throws Exception {
        for (ClientProvider provider : providers) {
            testXMLRPC89Vector(provider);
            testXMLRPC89Hashtable(provider);
            testXMLRPC89Properties(provider);
        }
    }

    private void testXMLRPC89Vector(ClientProvider pProvider) throws Exception {
        Vector<Object> values = new Vector<>();
        for (int i = 0;  i < 3;  i++) {
            values.add(i);
        }
        Vector<Object> params = new Vector<>();
        params.add(values);
        XmlRpcClient client = pProvider.getClient();
        client.setConfig(getConfig(pProvider));
        Object res = client.execute(XMLRPC89Handler.class.getName() + ".reverse", params);
        Object[] result = (Object[]) res;
        assertNotNull(result);
        assertEquals(3, result.length);
        for (int i = 0;  i < 3;  i++) {
            assertEquals(2 - i, result[i]);
        }

        ClientFactory factory = new ClientFactory(client);
        XMLRPC89Handler handler = (XMLRPC89Handler) factory.newInstance(XMLRPC89Handler.class);
        Vector<Object> resultVector = handler.reverse(values);
        assertNotNull(resultVector);
        assertEquals(3, resultVector.size());
        for (int i = 0;  i < 3;  i++) {
            assertEquals(2 - i, resultVector.get(i));
        }
    }

    private void verifyXMLRPC89Hashtable(Map<Object, Object> pMap) {
        assertNotNull(pMap);
        assertEquals(3, pMap.size());
        for (int i = 0;  i < 3;  i++) {
            Integer j = (Integer) pMap.get(String.valueOf(i));
            assertEquals(i*2, j.intValue());
        }
    }

    @SuppressWarnings("unchecked")
    private void testXMLRPC89Hashtable(ClientProvider pProvider) throws Exception {
        Hashtable<Object, Object> values = new Hashtable<>();
        for (int i = 0;  i < 3;  i++) {
            values.put(String.valueOf(i), i);
        }
        XmlRpcClient client = pProvider.getClient();
        client.setConfig(getConfig(pProvider));
        Map<Object, Object> res = (Map<Object, Object>) client.execute(XMLRPC89Handler.class.getName() + ".doubledValues", new Object[]{values});
        verifyXMLRPC89Hashtable(res);

        ClientFactory factory = new ClientFactory(client);
        XMLRPC89Handler handler = (XMLRPC89Handler) factory.newInstance(XMLRPC89Handler.class);
        Hashtable<Object, Object> result = handler.doubledValues(values);
        verifyXMLRPC89Hashtable(result);
    }

    private void verifyXMLRPC89Properties(Map<Object, Object> pMap) {
        assertNotNull(pMap);
        assertEquals(3, pMap.size());
        for (int i = 0;  i < 3;  i++) {
            String j = (String) pMap.get(String.valueOf(i));
            assertEquals(i*2, Integer.parseInt(j));
        }
    }

    @SuppressWarnings("unchecked")
    private void testXMLRPC89Properties(ClientProvider pProvider) throws Exception {
        Properties values = new Properties();
        for (int i = 0;  i < 3;  i++) {
            values.put(String.valueOf(i), String.valueOf(i));
        }
        XmlRpcClient client = pProvider.getClient();
        client.setConfig(getConfig(pProvider));
        Map<Object, Object> res = (Map<Object, Object>) client.execute(XMLRPC89Handler.class.getName() + ".doubledPropertyValues", new Object[]{values});
        verifyXMLRPC89Properties(res);
        ClientFactory factory = new ClientFactory(client);
        XMLRPC89Handler handler = (XMLRPC89Handler) factory.newInstance(XMLRPC89Handler.class);
        Properties result = handler.doubledPropertyValues(values);
        verifyXMLRPC89Properties(result);
    }

    /**
     * Handler for XMLRPC-96
     */
    public static class XMLRPC96Handler {
        /**
         * Returns the "Hello, world!" string.
         */
        public String getHelloWorld() {
            return "Hello, world!";
        }
    }

    /**
     * Test case for <a href="http://issues.apache.org/jira/browse/XMLRPC-96">
     * XMLRPC-96</a>
     */
    public void testXMLRPC96() throws Exception {
        for (ClientProvider provider : providers) {
            testXMLRPC96(provider);
        }
    }

    private void testXMLRPC96(ClientProvider pProvider) throws Exception {
        XmlRpcClient client = pProvider.getClient();
        client.setConfig(getConfig(pProvider));
        String s = (String) client.execute(XMLRPC96Handler.class.getName() + ".getHelloWorld", new Object[0]);
        assertEquals("Hello, world!", s);
        s = (String) client.execute(XMLRPC96Handler.class.getName() + ".getHelloWorld", (Object[]) null);
        assertEquals("Hello, world!", s);
    }

    /**
     * Test case for <a href="http://issues.apache.org/jira/browse/XMLRPC-112">
     * XMLRPC-112</a>
     */
    public void testXMLRPC112() throws Exception {
        for (ClientProvider provider : providers) {
            testXMLRPC112(provider);
        }
    }

    /**
     * Test case for <a href="http://issues.apache.org/jira/browse/XMLRPC-113">
     * XMLRPC-113</a>
     */
    public void testXMLRPC113() throws Exception {
        for (ClientProvider provider : providers) {
            testXMLRPC113(provider);
        }
    }


    private void testXMLRPC112(ClientProvider pProvider) throws Exception {
        XmlRpcClient client = pProvider.getClient();
        client.setConfig(getConfig(pProvider));
        TimingOutCallback toc = new TimingOutCallback(5000);
        final String methodName = XMLRPC89Handler.class.getName() + ".reverse";
        client.executeAsync(methodName, new Object[]{new Object[]{"1", "2", "3"}}, toc);
        Object o;
        try {
            o = toc.waitForResponse();
        } catch (Exception e) {
            throw e;
        } catch (Throwable t) {
            throw new UndeclaredThrowableException(t);
        }
        checkXMLRPC112Result(o);
        checkXMLRPC112Result(client.execute(methodName, new Object[]{new Object[]{"1", "2", "3"}}));
        checkXMLRPC112Result(client.execute(methodName, new Object[]{new Object[]{"1", "2", "3"}}));
    }

    private void checkXMLRPC112Result(Object pObject) {
        Object[] args = (Object[]) pObject;
        assertEquals(3, args.length);
        assertEquals("3", args[0]);
        assertEquals("2", args[1]);
        assertEquals("1", args[2]);
    }

    /**
     * Handler interface for {@link JiraTest#testXMLRPC113()}
     */ 
    public interface XMLRPC113Handler {
        /**
         * Throws an {@link XmlRpcException} with the given error code.
         */
        Object throwCode(int pCode) throws XmlRpcException;
    }

    /**
     * Handler for {@link JiraTest#testXMLRPC113()}
     */ 
    public static class XMLRPC113HandlerImpl implements XMLRPC113Handler {
        public Object throwCode(int pCode) throws XmlRpcException {
            throw new XmlRpcException(pCode, "Message: " + pCode);
        }
    }

    private void testXMLRPC113(ClientProvider pProvider) throws Exception {
        XmlRpcClient client = pProvider.getClient();
        client.setConfig(getConfig(pProvider));
        XMLRPC113Handler handler = (XMLRPC113Handler) new ClientFactory(client).newInstance(XMLRPC113Handler.class);
        for (int i = 0;  i < 5;  i++) {
            try {
                client.execute(XMLRPC113Handler.class.getName() + ".throwCode", new Object[] { i });
                fail("Excpected exception");
            } catch (XmlRpcException e) {
                assertEquals(i, e.code);
            }
            try {
                handler.throwCode(i);
            } catch (XmlRpcException e) {
                assertEquals(i, e.code);
            }
        }
    }

    /**
     * Handler for {@link JiraTest#testXMLRPC115()}
     */
    public static class XMLRPC115Handler {
        /**
         * Does nothing, just for checking, whether the server is alive.
         */
        public Object[] ping() {
            return new Object[0];
        }
    }

    /**
     * Test case for <a href="http://issues.apache.org/jira/browse/XMLRPC-115">
     * XMLRPC-115</a>
     */
    public void testXMLRPC115() throws Exception {
        for (ClientProvider provider : providers) {
            testXMLRPC115(provider);
        }
    }

    private void testXMLRPC115(ClientProvider pProvider) throws Exception {
        if (pProvider instanceof SunHttpTransportProvider) {
            XmlRpcClient client = pProvider.getClient();
            client.setConfig(getConfig(pProvider));
            URL url = ((XmlRpcHttpClientConfig) client.getConfig()).getServerURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("content-type", "text/xml");
            OutputStream ostream = conn.getOutputStream();
            Writer w = new OutputStreamWriter(ostream, StandardCharsets.UTF_8);
            w.write("<methodCall><methodName>" + XMLRPC115Handler.class.getName() + ".ping"
                    + "</methodName></methodCall>");
            w.close();
            InputStream istream = conn.getInputStream();
            XmlRpcResponseParser parser = new XmlRpcResponseParser((XmlRpcStreamRequestConfig) client.getClientConfig(), client.getTypeFactory());
            XMLReader xr = SAXParsers.newXMLReader();
            xr.setContentHandler(parser);
            xr.parse(new InputSource(istream));
            istream.close();
            assertTrue(parser.getResult() instanceof Object[]);
            assertEquals(0, ((Object[]) parser.getResult()).length);
        }
    }

    /**
     * Test case for <a href="http://issues.apache.org/jira/browse/XMLRPC-119">
     * XMLRPC-119</a>
     */
    public void testXMLRPC119() throws Exception {
        for (ClientProvider provider : providers) {
            testXMLRPC119(provider);
        }
    }

    /** Handler for XMLRPC-119
     */
    public static class XMLRPC119Handler {
        /** Returns a string with a length of "num" Kilobytes.
         */
        public String getString(int pSize) {
            StringBuilder sb = new StringBuilder(pSize*1024);
            for (int i = 0;  i < pSize*1024;  i++) {
                sb.append('&');
            }
            return sb.toString();
        }
    }

    private void testXMLRPC119(ClientProvider pProvider) throws Exception {
        XmlRpcClient client = pProvider.getClient();
        client.setConfig(getConfig(pProvider));
        for (int i = 0;  i < 100;  i+= 10) {
            String s = (String) client.execute(XMLRPC119Handler.class.getName() + ".getString",
                    new Object[] { i });
            assertEquals(i*1024, s.length());
        }
    }
}
