package org.xbib.netty.http.xmlrpc.server;

import org.xbib.netty.http.xmlrpc.common.XmlRpcException;

/**
 * This class implements the various "system" calls,
 * as specifies by {@link XmlRpcListableHandlerMapping}.
 * Suggested use is to create an instance and add it to
 * the handler mapping with the "system" prefix.
 */
public class XmlRpcSystemImpl {
    private XmlRpcListableHandlerMapping mapping;

    /** Creates a new instance, which provides meta data
     * for the given handler mappings methods.
     */
    public XmlRpcSystemImpl(XmlRpcListableHandlerMapping pMapping) {
        mapping = pMapping;
    }

    /** Implements the "system.methodSignature" call.
     * @see XmlRpcListableHandlerMapping#getMethodSignature(String)
     */
    public String[][] methodSignature(String methodName) throws XmlRpcException {
        return mapping.getMethodSignature(methodName);
    }

    /** Implements the "system.methodHelp" call.
     * @see XmlRpcListableHandlerMapping#getMethodHelp(String)
     */
    public String methodHelp(String methodName) throws XmlRpcException {
        return mapping.getMethodHelp(methodName);
    }

    /** Implements the "system.listMethods" call.
     * @see XmlRpcListableHandlerMapping#getListMethods()
     */
    public String[] listMethods() throws XmlRpcException {
        return mapping.getListMethods();
    }

    /**
     * Adds an instance of this class to the given handler
     * mapping.
     */
    public static void addSystemHandler(final PropertyHandlerMapping pMapping)
            throws XmlRpcException {
        final RequestProcessorFactoryFactory factory = pMapping.getRequestProcessorFactoryFactory();
        final XmlRpcSystemImpl systemHandler = new XmlRpcSystemImpl(pMapping);
        pMapping.setRequestProcessorFactoryFactory(pClass -> {
            if (XmlRpcSystemImpl.class.equals(pClass)) {
                return request -> systemHandler;
            } else {
                return factory.getRequestProcessorFactory(pClass);
            }
        });
        pMapping.addHandler("system", XmlRpcSystemImpl.class);
    }
}
