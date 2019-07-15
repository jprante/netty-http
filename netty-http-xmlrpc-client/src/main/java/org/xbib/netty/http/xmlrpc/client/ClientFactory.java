package org.xbib.netty.http.xmlrpc.client;

import org.xbib.netty.http.xmlrpc.common.TypeConverter;
import org.xbib.netty.http.xmlrpc.common.TypeConverterFactory;
import org.xbib.netty.http.xmlrpc.common.TypeConverterFactoryImpl;
import org.xbib.netty.http.xmlrpc.common.XmlRpcInvocationException;

import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;

/**
 * <p>The {@link ClientFactory} is a useful tool for simplifying the
 * use of Apache XML-RPC. The rough idea is as follows: All XML-RPC
 * handlers are implemented as interfaces. The server uses the actual
 * implementation. The client uses the {@link ClientFactory} to
 * obtain an implementation, which is based on running XML-RPC calls.</p>
 */
public class ClientFactory {

    private final XmlRpcClient client;

    private final TypeConverterFactory typeConverterFactory;

    private boolean objectMethodLocal;

    /** Creates a new instance.
     * @param pClient A fully configured XML-RPC client, which is
     *   used internally to perform XML-RPC calls.
     * @param pTypeConverterFactory Creates instances of {@link TypeConverterFactory},
     *   which are used to transform the result object in its target representation.
     */
    public ClientFactory(XmlRpcClient pClient, TypeConverterFactory pTypeConverterFactory) {
        typeConverterFactory = pTypeConverterFactory;
        client = pClient;
    }

    /** Creates a new instance. Shortcut for
     * <pre>
     *   new ClientFactory(pClient, new TypeConverterFactoryImpl());
     * </pre>
     * @param pClient A fully configured XML-RPC client, which is
     *   used internally to perform XML-RPC calls.
     * @see TypeConverterFactoryImpl
     */
    public ClientFactory(XmlRpcClient pClient) {
        this(pClient, new TypeConverterFactoryImpl());
    }

    /** Returns the factories client.
     */
    public XmlRpcClient getClient() {
        return client;
    }

    /** Returns, whether a method declared by the {@link Object
     * Object class} is performed by the local object, rather than
     * by the server. Defaults to true.
     */
    public boolean isObjectMethodLocal() {
        return objectMethodLocal;
    }

    /** Sets, whether a method declared by the {@link Object
     * Object class} is performed by the local object, rather than
     * by the server. Defaults to true.
     */
    public void setObjectMethodLocal(boolean pObjectMethodLocal) {
        objectMethodLocal = pObjectMethodLocal;
    }

    /**
     * Creates an object, which is implementing the given interface.
     * The objects methods are internally calling an XML-RPC server
     * by using the factories client; shortcut for
     * <pre>
     *   newInstance(Thread.currentThread().getContextClassLoader(),
     *     pClass)
     * </pre>
     */
    public Object newInstance(Class<?> pClass) {
        return newInstance(Thread.currentThread().getContextClassLoader(), pClass);
    }

    /** Creates an object, which is implementing the given interface.
     * The objects methods are internally calling an XML-RPC server
     * by using the factories client; shortcut for
     * <pre>
     *   newInstance(pClassLoader, pClass, pClass.getName())
     * </pre>
     */
    public Object newInstance(ClassLoader pClassLoader, Class<?> pClass) {
        return newInstance(pClassLoader, pClass, pClass.getName());
    }

    /** Creates an object, which is implementing the given interface.
     * The objects methods are internally calling an XML-RPC server
     * by using the factories client.
     * @param pClassLoader The class loader, which is being used for
     *   loading classes, if required.
     * @param pClass Interface, which is being implemented.
     * @param pRemoteName Handler name, which is being used when
     *   calling the server. This is used for composing the
     *   method name. For example, if <code>pRemoteName</code>
     *   is "Foo" and you want to invoke the method "bar" in
     *   the handler, then the full method name would be "Foo.bar".
     */
    public Object newInstance(ClassLoader pClassLoader, final Class<?> pClass, final String pRemoteName) {

       return Proxy.newProxyInstance(pClassLoader, new Class<?>[] { pClass }, (pProxy, pMethod, pArgs) -> {
           if (isObjectMethodLocal()  &&  pMethod.getDeclaringClass().equals(Object.class)) {
               return pMethod.invoke(pProxy, pArgs);
           }
           final String methodName;
           if (pRemoteName == null  ||  pRemoteName.length() == 0) {
               methodName = pMethod.getName();
           } else {
               methodName = pRemoteName + "." + pMethod.getName();
           }
           Object result;
           try {
               result = client.execute(methodName, pArgs);
           } catch (XmlRpcInvocationException e) {
               Throwable t = e.linkedException;
               if (t instanceof RuntimeException) {
                   throw t;
               }
               Class<?>[] exceptionTypes = pMethod.getExceptionTypes();
               for (Class<?> c : exceptionTypes) {
                   if (c.isAssignableFrom(t.getClass())) {
                       throw t;
                   }
               }
               throw new UndeclaredThrowableException(t);
           }
           TypeConverter typeConverter = typeConverterFactory.getTypeConverter(pMethod.getReturnType());
           return typeConverter.convert(result);
       });
    }
}
