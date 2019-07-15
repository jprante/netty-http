package org.xbib.netty.http.xmlrpc.client;

import org.xbib.netty.http.xmlrpc.common.TypeConverter;
import org.xbib.netty.http.xmlrpc.common.TypeConverterFactory;
import org.xbib.netty.http.xmlrpc.common.XmlRpcConfig;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcExtensionException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequest;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequestProcessor;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * The default implementation of a local transport.
 */
public class XmlRpcLocalTransport extends XmlRpcTransportImpl {

    /**
     * Creates a new instance.
     * @param pClient The client, which creates the transport.
     */
    public XmlRpcLocalTransport(XmlRpcClient pClient) {
        super(pClient);
    }

    @SuppressWarnings("unchecked")
    private boolean isExtensionType(Object pObject) {
        if (pObject == null) {
            return true;
        } else if (pObject instanceof Object[]) {
            Object[] objects = (Object[]) pObject;
            for (Object object : objects) {
                if (isExtensionType(object)) {
                    return true;
                }
            }
            return false;
        } else if (pObject instanceof Collection) {
            for (Object o : ((Collection) pObject)) {
                if (isExtensionType(o)) {
                    return true;
                }
            }
            return false;
        } else if (pObject instanceof Map) {
            Map<String, Object> map = (Map) pObject;
            for (Object o : map.entrySet()) {
                Map.Entry<String, Object> entry = (Map.Entry) o;
                if (isExtensionType(entry.getKey()) || isExtensionType(entry.getValue())) {
                    return true;
                }
            }
            return false;
        } else {
            return !(pObject instanceof Integer
                    ||  pObject instanceof Date
                    ||  pObject instanceof String
                    ||  pObject instanceof byte[]
                    ||  pObject instanceof Double);
        }
    }

    public Object sendRequest(XmlRpcRequest pRequest) throws XmlRpcException {
        XmlRpcConfig config = pRequest.getConfig();
        if (!config.isEnabledForExtensions()) {
            for (int i = 0;  i < pRequest.getParameterCount();  i++) {
                if (isExtensionType(pRequest.getParameter(i))) {
                    throw new XmlRpcExtensionException("Parameter " + i + " has invalid type, if isEnabledForExtensions() == false");
                }
            }
        }
        final XmlRpcRequestProcessor server = ((XmlRpcLocalClientConfig) config).getXmlRpcServer();
        Object result;
        try {
            result = server.execute(pRequest);
        } catch (XmlRpcException t) {
            throw t;
        } catch (Throwable t) {
            throw new XmlRpcClientException("Failed to invoke method " + pRequest.getMethodName()
                    + ": " + t.getMessage(), t);
        }
        if (!config.isEnabledForExtensions()) {
            if (isExtensionType(result)) {
                throw new XmlRpcExtensionException("Result has invalid type, if isEnabledForExtensions() == false");
            }
        }

        if (result == null) {
            return null;
        }
        final TypeConverterFactory typeConverterFactory = server.getTypeConverterFactory();
        final TypeConverter typeConverter = typeConverterFactory.getTypeConverter(result.getClass());
        return typeConverter.backConvert(result);
    }
}
