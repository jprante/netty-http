package org.xbib.netty.http.xmlrpc.client.test;

import org.xbib.netty.http.xmlrpc.client.XmlRpcClient;
import org.xbib.netty.http.xmlrpc.client.XmlRpcClientConfig;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.server.PropertyHandlerMapping;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHandlerMapping;
import org.xbib.netty.http.xmlrpc.server.XmlRpcSystemImpl;

import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;

/**
 * Test class for the introspection stuff.
 */
public class MetadataTest extends XmlRpcTestCase {

    @Override
    protected XmlRpcHandlerMapping getHandlerMapping() throws XmlRpcException {
        PropertyHandlerMapping mapping = new PropertyHandlerMapping();
        mapping.addHandler("Adder", AuthenticationTest.AdderImpl.class);
        XmlRpcSystemImpl.addSystemHandler(mapping);
        return mapping;
    }

    /**
     * Test, whether the actual handlers are working.
     */
    public void testAdder() throws Exception {
        for (ClientProvider provider : providers) {
            testAdder(provider);
        }
    }

    private void testAdder(ClientProvider pProvider) throws Exception {
        XmlRpcClient client = pProvider.getClient();
        XmlRpcClientConfig config = getConfig(pProvider);
        client.setConfig(config);
        Object o = client.execute("Adder.add", new Object[]{3, 5});
        assertEquals(8, o);
    }

    /**
     * Test for system.listMethods.
     */
    public void testListMethods() throws Exception {
        for (ClientProvider provider : providers) {
            testListMethods(provider);
        }
    }

    private void testListMethods(ClientProvider pProvider) throws Exception {
        XmlRpcClient client = pProvider.getClient();
        XmlRpcClientConfig config = getConfig(pProvider);
        client.setConfig(config);
        Object o = client.execute("system.listMethods", new Object[0]);
        Object[] methodList = (Object[]) o;
        Arrays.sort(methodList, Collator.getInstance(Locale.US));
        assertEquals(4, methodList.length);
        assertEquals("Adder.add", methodList[0]);
        assertEquals("system.listMethods", methodList[1]);
        assertEquals("system.methodHelp", methodList[2]);
        assertEquals("system.methodSignature", methodList[3]);
    }

    /**
     * Test for system.methodHelp.
     */
    public void testMethodHelp() throws Exception {
        for (ClientProvider provider : providers) {
            testMethodHelp(provider);
        }
    }

    private void testMethodHelp(ClientProvider pProvider) throws Exception {
        XmlRpcClient client = pProvider.getClient();
        XmlRpcClientConfig config = getConfig(pProvider);
        client.setConfig(config);
        String help = (String) client.execute("system.methodHelp", new Object[]{"Adder.add"});
        assertEquals("Invokes the method org.xbib.netty.http.xmlrpc.client.test.AuthenticationTest$AdderImpl.add(int, int).", help);
    }

    /**
     * Test for system.methodSignature.
     */
    public void testMethodSignature() throws Exception {
        for (ClientProvider provider : providers) {
            testMethodSignature(provider);
        }
    }

    private void testMethodSignature(ClientProvider pProvider) throws Exception {
        XmlRpcClient client = pProvider.getClient();
        XmlRpcClientConfig config = getConfig(pProvider);
        client.setConfig(config);
        Object[] signatures = (Object[]) client.execute("system.methodSignature", new Object[]{"Adder.add"});
        assertEquals(signatures.length, 1);
        Object[] signature = (Object[]) signatures[0];
        assertEquals(3, signature.length);
        assertEquals("int", signature[0]);
        assertEquals("int", signature[1]);
        assertEquals("int", signature[2]);
    }
}
