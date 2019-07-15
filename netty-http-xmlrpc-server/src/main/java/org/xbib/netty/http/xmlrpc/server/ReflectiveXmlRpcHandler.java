package org.xbib.netty.http.xmlrpc.server;

import org.xbib.netty.http.xmlrpc.common.TypeConverter;
import org.xbib.netty.http.xmlrpc.common.TypeConverterFactory;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcHandler;
import org.xbib.netty.http.xmlrpc.common.XmlRpcInvocationException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcNotAuthorizedException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Default implementation of {@link XmlRpcHandler}.
 */
public class ReflectiveXmlRpcHandler implements XmlRpcHandler {

    private static class MethodData {

        final Method method;

        final TypeConverter[] typeConverters;

        MethodData(Method pMethod, TypeConverterFactory pTypeConverterFactory) {
            method = pMethod;
            Class<?>[] paramClasses = method.getParameterTypes();
            typeConverters = new TypeConverter[paramClasses.length];
            for (int i = 0;  i < paramClasses.length;  i++) {
                typeConverters[i] = pTypeConverterFactory.getTypeConverter(paramClasses[i]);
            }
        }
    }

    private final AbstractReflectiveHandlerMapping mapping;

    private final MethodData[] methods;

    private final Class<?> clazz;

    private final RequestProcessorFactoryFactory.RequestProcessorFactory requestProcessorFactory;

	/** Creates a new instance.
	 * @param pMapping The mapping, which creates this handler.
	 * @param pClass The class, which has been inspected to create
	 * this handler. Typically, this will be the same as
	 * <pre>pInstance.getClass()</pre>. It is used for diagnostic
	 * messages only.
	 * @param pMethods The method, which will be invoked for
	 * executing the handler. 
	 */
	public ReflectiveXmlRpcHandler(AbstractReflectiveHandlerMapping pMapping,
                                   TypeConverterFactory pTypeConverterFactory,
                                   Class<?> pClass, RequestProcessorFactoryFactory.RequestProcessorFactory pFactory, Method[] pMethods) {
		mapping = pMapping;
        clazz = pClass;
		methods = new MethodData[pMethods.length];
        requestProcessorFactory = pFactory;
        for (int i = 0;  i < methods.length;  i++) {
            methods[i] = new MethodData(pMethods[i], pTypeConverterFactory); 
        }
	}

    private Object getInstance(XmlRpcRequest pRequest) throws XmlRpcException {
        return requestProcessorFactory.getRequestProcessor(pRequest);
    }

	public Object execute(XmlRpcRequest pRequest) throws XmlRpcException {
	    AbstractReflectiveHandlerMapping.AuthenticationHandler authHandler = mapping.getAuthenticationHandler();
	    if (authHandler != null  &&  !authHandler.isAuthorized(pRequest)) {
	        throw new XmlRpcNotAuthorizedException("Not authorized");
	    }
	    Object[] args = new Object[pRequest.getParameterCount()];
	    for (int j = 0;  j < args.length;  j++) {
	        args[j] = pRequest.getParameter(j);
	    }
	    Object instance = getInstance(pRequest);
        for (MethodData methodData : methods) {
            TypeConverter[] converters = methodData.typeConverters;
            if (args.length == converters.length) {
                boolean matching = true;
                for (int j = 0; j < args.length; j++) {
                    if (!converters[j].isConvertable(args[j])) {
                        matching = false;
                        break;
                    }
                }
                if (matching) {
                    for (int j = 0; j < args.length; j++) {
                        args[j] = converters[j].convert(args[j]);
                    }
                    return invoke(instance, methodData.method, args);
                }
            }
        }
	    throw new XmlRpcException("No method matching arguments: " + Util.getSignature(args));
    }

    private Object invoke(Object pInstance, Method pMethod, Object[] pArgs) throws XmlRpcException {
        try {
	        return pMethod.invoke(pInstance, pArgs);
	    } catch (IllegalAccessException e) {
	        throw new XmlRpcException("Illegal access to method "
	                                  + pMethod.getName() + " in class "
	                                  + clazz.getName(), e);
	    } catch (IllegalArgumentException e) {
	        throw new XmlRpcException("Illegal argument for method "
	                                  + pMethod.getName() + " in class "
	                                  + clazz.getName(), e);
	    } catch (InvocationTargetException e) {
	        Throwable t = e.getTargetException();
            if (t instanceof XmlRpcException) {
                throw (XmlRpcException) t;
            }
	        throw new XmlRpcInvocationException("Failed to invoke method "
	                                  + pMethod.getName() + " in class "
	                                  + clazz.getName() + ": "
	                                  + t.getMessage(), t);
	    }
	}
}
