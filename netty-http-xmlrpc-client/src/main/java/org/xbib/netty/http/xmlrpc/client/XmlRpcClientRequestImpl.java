package org.xbib.netty.http.xmlrpc.client;

import org.xbib.netty.http.xmlrpc.common.XmlRpcRequest;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequestConfig;

import java.util.List;

/**
 * Default implementation of
 * {@link XmlRpcRequest}.
 */
public class XmlRpcClientRequestImpl implements XmlRpcRequest {

    private static final Object[] ZERO_PARAMS = new Object[0];

    private final XmlRpcRequestConfig config;

    private final String methodName;

    private final Object[] params;

    /**
     * Creates a new instance.
     * @param pConfig The request configuration.
     * @param pMethodName The method name being performed.
     * @param pParams The parameters.
     * @throws NullPointerException One of the parameters is null.
     */
    public XmlRpcClientRequestImpl(XmlRpcRequestConfig pConfig,
                                   String pMethodName, Object[] pParams) {
        config = pConfig;
        if (config == null) {
            throw new NullPointerException("The request configuration must not be null.");
        }
        methodName = pMethodName;
        if (methodName == null) {
            throw new NullPointerException("The method name must not be null.");
        }
        params = pParams == null ? ZERO_PARAMS : pParams;
    }

    /**
     * Creates a new instance.
     * @param pConfig The request configuration.
     * @param pMethodName The method name being performed.
     * @param pParams The parameters.
     * @throws NullPointerException The method name or the parameters are null.
     */
    public XmlRpcClientRequestImpl(XmlRpcRequestConfig pConfig,
                                   String pMethodName, List<Object> pParams) {
        this(pConfig, pMethodName, pParams == null ? null : pParams.toArray());
    }

    public String getMethodName() { return methodName; }

    public int getParameterCount() { return params.length; }

    public Object getParameter(int pIndex) { return params[pIndex]; }

    public XmlRpcRequestConfig getConfig() { return config; }
}
