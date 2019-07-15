package org.xbib.netty.http.xmlrpc.client;

import org.xbib.netty.http.xmlrpc.common.XmlRpcConfig;
import org.xbib.netty.http.xmlrpc.common.XmlRpcController;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequest;
import org.xbib.netty.http.xmlrpc.common.XmlRpcWorkerFactory;
import org.xbib.netty.http.xmlrpc.common.serializer.XmlWriterFactory;

import java.util.List;

/**
 * <p>The main access point of an XML-RPC client. This object serves mainly
 * as an object factory. It is designed with singletons in mind: Basically,
 * an application should be able to hold a single instance of
 * <code>XmlRpcClient</code> in a static variable, unless you would be
 * working with different factories.</p>
 * <p>Until Apache XML-RPC 2.0, this object was used both as an object
 * factory and as a place, where configuration details (server URL,
 * suggested encoding, user credentials and the like) have been stored.
 * In Apache XML-RPC 3.0, the configuration details has been moved to
 * the {@link XmlRpcClientConfig} object.
 * The configuration object is designed for being passed through the
 * actual worker methods.</p>
 * <p>A configured XmlRpcClient object is thread safe: In other words,
 * the suggested use is, that you configure the client using
 * {@link #setTransportFactory(XmlRpcTransportFactory)} and similar
 * methods, store it in a field and never modify it again. Without
 * modifications, the client may be used for an arbitrary number
 * of concurrent requests.</p>
 */
public class XmlRpcClient extends XmlRpcController {

    private XmlRpcTransportFactory transportFactory = XmlRpcClientDefaults.newTransportFactory(this);

    private XmlRpcClientConfig config = XmlRpcClientDefaults.newXmlRpcClientConfig();

    private XmlWriterFactory xmlWriterFactory = XmlRpcClientDefaults.newXmlWriterFactory();

    protected XmlRpcWorkerFactory getDefaultXmlRpcWorkerFactory() {
        return new XmlRpcClientWorkerFactory(this);
    }

    /**
     * Sets the clients default configuration. This configuration
     * is used by the methods
     * {@link #execute(String, List)},
     * {@link #execute(String, Object[])}, and
     * {@link #execute(XmlRpcRequest)}.
     * You may overwrite this per request by using
     * {@link #execute(XmlRpcClientConfig, String, List)},
     * or {@link #execute(XmlRpcClientConfig, String, Object[])}.
     * @param pConfig The default request configuration.
     */
    public void setConfig(XmlRpcClientConfig pConfig) {
        config = pConfig;
    }

    /**
     * Returns the clients default configuration. This configuration
     * is used by the methods
     * {@link #execute(String, List)},
     * {@link #execute(String, Object[])}.
     * You may overwrite this per request by using
     * {@link #execute(XmlRpcClientConfig, String, List)},
     * or {@link #execute(XmlRpcClientConfig, String, Object[])}.
     * @return The default request configuration.
     */
    public XmlRpcConfig getConfig() {
        return config;
    }

    /**
     * Returns the clients default configuration. Shortcut for
     * <code>(XmlRpcClientConfig) getConfig()</code>.
     * This configuration is used by the methods
     * {@link #execute(String, List)},
     * {@link #execute(String, Object[])}.
     * You may overwrite this per request by using
     * {@link #execute(XmlRpcClientConfig, String, List)}, or
     * {@link #execute(XmlRpcClientConfig, String, Object[])}
     * @return The default request configuration.
     */
    public XmlRpcClientConfig getClientConfig() {
        return config;
    }

    /**
     * Sets the clients transport factory. The client will invoke the
     * factory method {@link XmlRpcTransportFactory#getTransport()}
     * for any request.
     * @param pFactory The clients transport factory.
     */
    public void setTransportFactory(XmlRpcTransportFactory pFactory) {
        transportFactory = pFactory;
    }

    /**
     * Returns the clients transport factory. The client will use this factory
     * for invocation of {@link XmlRpcTransportFactory#getTransport()}
     * for any request.
     * @return The clients transport factory.
     */
    public XmlRpcTransportFactory getTransportFactory() {
        return transportFactory;
    }

    /**
     * Performs a request with the clients default configuration.
     * @param pMethodName The method being performed.
     * @param pParams The parameters.
     * @return The result object.
     * @throws XmlRpcException Performing the request failed.
     */
    public Object execute(String pMethodName, Object[] pParams) throws XmlRpcException {
        return execute(getClientConfig(), pMethodName, pParams);
    }

    /**
     * Performs a request with the given configuration.
     * @param pConfig The request configuration.
     * @param pMethodName The method being performed.
     * @param pParams The parameters.
     * @return The result object.
     * @throws XmlRpcException Performing the request failed.
     */
    public Object execute(XmlRpcClientConfig pConfig, String pMethodName, Object[] pParams) throws XmlRpcException {
        return execute(new XmlRpcClientRequestImpl(pConfig, pMethodName, pParams));
    }

    /**
     * Performs a request with the clients default configuration.
     * @param pMethodName The method being performed.
     * @param pParams The parameters.
     * @return The result object.
     * @throws XmlRpcException Performing the request failed.
     */
    public Object execute(String pMethodName, List<Object> pParams) throws XmlRpcException {
        return execute(getClientConfig(), pMethodName, pParams);
    }

    /**
     * Performs a request with the given configuration.
     * @param pConfig The request configuration.
     * @param pMethodName The method being performed.
     * @param pParams The parameters.
     * @return The result object.
     * @throws XmlRpcException Performing the request failed.
     */
    public Object execute(XmlRpcClientConfig pConfig, String pMethodName, List<Object> pParams) throws XmlRpcException {
        return execute(new XmlRpcClientRequestImpl(pConfig, pMethodName, pParams));
    }

    /**
     * Performs a request with the clients default configuration.
     * @param pRequest The request being performed.
     * @return The result object.
     * @throws XmlRpcException Performing the request failed.
     */
    public Object execute(XmlRpcRequest pRequest) throws XmlRpcException {
        return getWorkerFactory().getWorker().execute(pRequest);
    }

    /**
     * Performs an asynchronous request with the clients default configuration.
     * @param pMethodName The method being performed.
     * @param pParams The parameters.
     * @param pCallback The callback being notified when the request is finished.
     * @throws XmlRpcException Performing the request failed.
     */
    public void executeAsync(String pMethodName, Object[] pParams,
                             AsyncCallback pCallback) throws XmlRpcException {
        executeAsync(getClientConfig(), pMethodName, pParams, pCallback);
    }

    /**
     * Performs an asynchronous request with the given configuration.
     * @param pConfig The request configuration.
     * @param pMethodName The method being performed.
     * @param pParams The parameters.
     * @param pCallback The callback being notified when the request is finished.
     * @throws XmlRpcException Performing the request failed.
     */
    public void executeAsync(XmlRpcClientConfig pConfig,
                             String pMethodName, Object[] pParams,
                             AsyncCallback pCallback) throws XmlRpcException {
        executeAsync(new XmlRpcClientRequestImpl(pConfig, pMethodName, pParams),
                pCallback);
    }

    /**
     * Performs an asynchronous request with the clients default configuration.
     * @param pMethodName The method being performed.
     * @param pParams The parameters.
     * @param pCallback The callback being notified when the request is finished.
     * @throws XmlRpcException Performing the request failed.
     */
    public void executeAsync(String pMethodName, List<Object> pParams,
                             AsyncCallback pCallback) throws XmlRpcException {
        executeAsync(getClientConfig(), pMethodName, pParams, pCallback);
    }

    /**
     * Performs an asynchronous request with the given configuration.
     * @param pConfig The request configuration.
     * @param pMethodName The method being performed.
     * @param pParams The parameters.
     * @param pCallback The callback being notified when the request is finished.
     * @throws XmlRpcException Performing the request failed.
     */
    public void executeAsync(XmlRpcClientConfig pConfig,
                             String pMethodName, List<Object> pParams,
                             AsyncCallback pCallback) throws XmlRpcException {
        executeAsync(new XmlRpcClientRequestImpl(pConfig, pMethodName, pParams), pCallback);
    }

    /**
     * Performs a request with the clients default configuration.
     * @param pRequest The request being performed.
     * @param pCallback The callback being notified when the request is finished.
     * @throws XmlRpcException Performing the request failed.
     */
    public void executeAsync(XmlRpcRequest pRequest,
                             AsyncCallback pCallback) throws XmlRpcException {
        XmlRpcClientWorker w = (XmlRpcClientWorker) getWorkerFactory().getWorker();
        w.execute(pRequest, pCallback);
    }

    /**
     * Returns the clients instance of
     * {@link XmlWriterFactory}.
     * @return A factory for creating instances.
     */
    public XmlWriterFactory getXmlWriterFactory() {
        return xmlWriterFactory;
    }

    /**
     * Sets the clients instance of
     * {@link XmlWriterFactory}.
     * @param pFactory A factory for creating instances}.
     */
    public void setXmlWriterFactory(XmlWriterFactory pFactory) {
        xmlWriterFactory = pFactory;
    }
}
