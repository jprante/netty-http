package org.xbib.netty.http.xmlrpc.server;

import org.xbib.netty.http.xmlrpc.common.TypeConverterFactory;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;

import java.lang.reflect.Method;

/** Default implementation of {@link XmlRpcMetaDataHandler}.
 */
public class ReflectiveXmlRpcMetaDataHandler extends ReflectiveXmlRpcHandler
        implements XmlRpcMetaDataHandler {
    private final String[][] signatures;
    private final String methodHelp;

    /** Creates a new instance.
     * @param pMapping The mapping, which creates this handler.
     * @param pClass The class, which has been inspected to create
     * this handler. Typically, this will be the same as
     * <pre>pInstance.getClass()</pre>. It is used for diagnostic
     * messages only.
     * @param pMethods The method, which will be invoked for
     * executing the handler.
     * @param pSignatures The signature, which will be returned by
     * {@link #getSignatures()}.
     * @param pMethodHelp The help string, which will be returned
     * by {@link #getMethodHelp()}.
     */
    public ReflectiveXmlRpcMetaDataHandler(AbstractReflectiveHandlerMapping pMapping,
                                           TypeConverterFactory pTypeConverterFactory,
                                           Class<?> pClass, RequestProcessorFactoryFactory.RequestProcessorFactory pFactory,
                                           Method[] pMethods,
                                           String[][] pSignatures, String pMethodHelp) {
        super(pMapping, pTypeConverterFactory, pClass, pFactory, pMethods);
        signatures = pSignatures;
        methodHelp = pMethodHelp;
    }

    public String[][] getSignatures() throws XmlRpcException {
        return signatures;
    }

    public String getMethodHelp() throws XmlRpcException {
        return methodHelp;
    }
}
