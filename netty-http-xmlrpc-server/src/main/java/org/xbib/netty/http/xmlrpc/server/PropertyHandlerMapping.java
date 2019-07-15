package org.xbib.netty.http.xmlrpc.server;

import org.xbib.netty.http.xmlrpc.common.XmlRpcException;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

/**
 * A handler mapping based on a property file. The property file
 * contains a set of properties. The property key is taken as the
 * handler name. The property value is taken as the name of a
 * class being instantiated. For any non-void, non-static, and
 * public method in the class, an entry in the handler map is
 * generated. A typical use would be, to specify interface names
 * as the property keys and implementations as the values.
 */
public class PropertyHandlerMapping extends AbstractReflectiveHandlerMapping {
    /**
     * Reads handler definitions from a resource file.
     * @param pClassLoader The class loader being used to load
     *   handler classes.
     * @param pResource The resource being used, for example
     *   "org/apache/xmlrpc/webserver/XmlRpcServlet.properties"
     * @throws IOException Loading the property file failed.
     * @throws XmlRpcException Initializing the handlers failed.
     */
    public void load(ClassLoader pClassLoader, String pResource)
            throws IOException, XmlRpcException {
        URL url = pClassLoader.getResource(pResource);
        if (url == null) {
            throw new IOException("Unable to locate resource " + pResource);
        }
        load(pClassLoader, url);
    }
    
    /**
     * Reads handler definitions from a property file.
     * @param pClassLoader The class loader being used to load
     *   handler classes.
     * @param pURL The URL from which to load the property file
     * @throws IOException Loading the property file failed.
     * @throws XmlRpcException Initializing the handlers failed.
     */
    public void load(ClassLoader pClassLoader, URL pURL) throws IOException, XmlRpcException {
        Properties props = new Properties();
        props.load(pURL.openStream());
        load(pClassLoader, props);
    }

    /**
     * Reads handler definitions from an existing Map.
     * @param pClassLoader The class loader being used to load
     *   handler classes.
     * @param pMap The existing Map to read from
     * @throws XmlRpcException Initializing the handlers failed.
     */
    public void load(ClassLoader pClassLoader, Map<Object, Object> pMap) throws XmlRpcException {
        for (Map.Entry<Object, Object> entry : pMap.entrySet()) {
            String key = entry.getKey().toString();
            String value = entry.getValue().toString();
            Class<?> c = newHandlerClass(pClassLoader, value);
            registerPublicMethods(key, c);
        }
    }

    protected Class<?> newHandlerClass(ClassLoader pClassLoader, String pClassName)
            throws XmlRpcException {
        final Class<?> c;
        try {
            c = pClassLoader.loadClass(pClassName);
        } catch (ClassNotFoundException e) {
            throw new XmlRpcException("Unable to load class: " + pClassName, e);
        }
        if (c == null) {
            throw new XmlRpcException(0, "Loading class " + pClassName + " returned null.");
        }
        return c;
    }

    /**
     * Adds handlers for the given object to the mapping.
     * The handlers are build by invoking
     * {@link #registerPublicMethods(String, Class)}.
     * @param pKey The class key, which is passed
     * to {@link #registerPublicMethods(String, Class)}.
     * @param pClass Class, which is responsible for handling the request.
     */
    public void addHandler(String pKey, Class<?> pClass) throws XmlRpcException {
        registerPublicMethods(pKey, pClass);
    }

    /**
     * Removes all handlers with the given class key.
     */
    public void removeHandler(String pKey) {
        handlerMap.keySet().removeIf(k -> k.startsWith(pKey));
    }
}
