package org.xbib.netty.http.xmlrpc.client.test;

import java.io.IOException;

import org.xbib.netty.http.xmlrpc.client.ClientFactory;
import org.xbib.netty.http.xmlrpc.client.XmlRpcClient;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHandlerMapping;
import org.xml.sax.SAXException;

/** Test case for the {@link ClientFactory}.
 */
public class DynamicProxyTest extends XmlRpcTestCase {
    /** An interface, which is being implemented by the
     * server.
     */
    public interface Adder {
        /** Returns the sum of the given integers.
         */
        public int add(int pNum1, int pNum2);

        /**
         * Throws a SAXException.
         */
        public Object parse(String pMessage) throws SAXException;

        /**
         * A void method; these are disabled without support for
         * extensions, but enabled when extensions are on.
         */
        public void ping();
    }

    /** Implementation of {@link Adder}, which is used by
     * the server.
     */
    public static class AdderImpl implements Adder {
        public int add(int pNum1, int pNum2) {
            return pNum1 + pNum2;
        }
        public Object parse(String pMessage) throws SAXException {
            throw new SAXException("Failed to parse message: " + pMessage);
        }
        public void ping() {
        }
    }

    protected XmlRpcHandlerMapping getHandlerMapping() throws IOException, XmlRpcException {
        return getHandlerMapping("DynamicProxyTest.properties");
    }

    private ClientFactory getClientFactory(ClientProvider pProvider) throws Exception {
        XmlRpcClient client = pProvider.getClient();
        client.setConfig(getConfig(pProvider));
        return new ClientFactory(client);
    }

    private ClientFactory getExClientFactory(ClientProvider pProvider) throws Exception {
        XmlRpcClient client = pProvider.getClient();
        client.setConfig(getExConfig(pProvider));
        return new ClientFactory(client);
    }

    /** Tests calling the {@link Adder#add(int,int)} method
     * by using an object, which has been created by the
     * {@link ClientFactory}.
     */
    public void testAdderCall() throws Exception {
        for (int i = 0;  i < providers.length;  i++) {
            testAdderCall(providers[i]);
        }
    }

    private void testAdderCall(ClientProvider pProvider) throws Exception {
        ClientFactory factory = getClientFactory(pProvider);
        Adder adder = (Adder) factory.newInstance(Adder.class);
        assertEquals(6, adder.add(2, 4));
    }

    /** Tests trapping a SAXException.
     */
    public void testParseCall() throws Exception {
        for (int i = 0;   i < providers.length;  i++) {
            testParseCall(providers[i]);
        }
    }

    private void testParseCall(ClientProvider pProvider) throws Exception {
        ClientFactory factory = getExClientFactory(pProvider);
        Adder adder = (Adder) factory.newInstance(Adder.class);
        try {
            adder.parse("foo");
            fail("Expected SAXException");
        } catch (SAXException e) {
            // Ok
        }
    }

    /**
     * Tests invoking a "void" method.
     */
    public void testVoidMethod() throws Exception {
        for (int i = 0;   i < providers.length;  i++) {
            testVoidMethod(providers[i]);
        }
    }

    private void testVoidMethod(ClientProvider pProvider) throws Exception {
        ClientFactory factory = getExClientFactory(pProvider);
        Adder adder = (Adder) factory.newInstance(Adder.class);
        adder.ping();
    }
}
