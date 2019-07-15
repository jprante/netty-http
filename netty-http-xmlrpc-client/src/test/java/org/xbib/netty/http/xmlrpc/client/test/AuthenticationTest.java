package org.xbib.netty.http.xmlrpc.client.test;

import org.xbib.netty.http.xmlrpc.client.ClientFactory;
import org.xbib.netty.http.xmlrpc.client.XmlRpcClient;
import org.xbib.netty.http.xmlrpc.client.XmlRpcClientConfigImpl;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcHttpRequestConfig;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequest;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequestConfig;
import org.xbib.netty.http.xmlrpc.server.AbstractReflectiveHandlerMapping;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHandlerMapping;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test case for supported authentication variants.
 */
public class AuthenticationTest extends XmlRpcTestCase {
    private static final String PASSWORD = "98765432109876543210987654321098765432109876543210";
    private static final String USER_NAME = "01234567890123456789012345678901234567890123456789"
        + "\u00C4\u00D6\u00DC\u00F6\u00FC\u00E4\u00DF";

    /** An interface, which is being implemented by the
     * server.
     */
    public interface Adder {
        /** Returns the sum of the given integers.
         */
        public int add(int pNum1, int pNum2);
    }

    /** Implementation of {@link DynamicProxyTest.Adder}, which is used by
     * the server.
     */
    public static class AdderImpl implements Adder {
        public int add(int pNum1, int pNum2) {
            return pNum1 + pNum2;
        }
    }

    protected XmlRpcHandlerMapping getHandlerMapping() throws IOException, XmlRpcException {
        XmlRpcHandlerMapping mapping = getHandlerMapping("AuthenticationTest.properties");
        ((AbstractReflectiveHandlerMapping) mapping).setAuthenticationHandler(new AbstractReflectiveHandlerMapping.AuthenticationHandler(){
            public boolean isAuthorized(XmlRpcRequest pRequest)
                    throws XmlRpcException {
                XmlRpcRequestConfig config = pRequest.getConfig();
                if (config instanceof XmlRpcHttpRequestConfig) {
                    XmlRpcHttpRequestConfig httpRequestConfig = (XmlRpcHttpRequestConfig) config;
                    return USER_NAME.equals(httpRequestConfig.getBasicUserName())
                        &&  PASSWORD.equals(httpRequestConfig.getBasicPassword());
                }
                return true;
            }
        });
        return mapping;
    }

    protected XmlRpcClientConfigImpl getConfig(ClientProvider pProvider)
            throws Exception {
        XmlRpcClientConfigImpl config = super.getConfig(pProvider);
        config.setBasicUserName(USER_NAME);
        config.setBasicPassword(PASSWORD);
        return config;
    }

    private ClientFactory getClientFactory(ClientProvider pProvider) throws Exception {
        XmlRpcClient client = pProvider.getClient();
        client.setConfig(getConfig(pProvider));
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
}
