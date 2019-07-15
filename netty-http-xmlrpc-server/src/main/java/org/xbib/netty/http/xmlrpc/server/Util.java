package org.xbib.netty.http.xmlrpc.server;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Node;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;

/** Utility class, which provides services to meta data
 * handlers and handler mappings.
 */
public class Util {
	/** This field should solve the problem, that we do not
	 * want to depend on the presence of JAXB. However, if
	 * it is available, we want to support it.
	 */
	private static final Class<?> jaxbElementClass;
	static {
		Class<?> c;
		try {
			c = Class.forName("javax.xml.bind.Element");
		} catch (ClassNotFoundException e) {
			c = null;
		}
		jaxbElementClass = c;
	}
	
	/** Returns a signature for the given return type or
	 * parameter class.
	 * @param pType The class for which a signature is being
	 * queried.
	 * @return Signature, if known, or null.
	 */
	public static String getSignatureType(Class<?> pType) {
		if (pType == Integer.TYPE || pType == Integer.class)
			return "int";
		if (pType == Double.TYPE || pType == Double.class)
			return "double";
		if (pType == Boolean.TYPE || pType == Boolean.class)
			return "boolean";
		if (pType == String.class)
			return "string";
		if (Object[].class.isAssignableFrom(pType)
			||  List.class.isAssignableFrom(pType))
			return "array";
		if (Map.class.isAssignableFrom(pType))
			return "struct";
		if (Date.class.isAssignableFrom(pType)
			||  Calendar.class.isAssignableFrom(pType))
			return "dateTime.iso8601";
		if (pType == byte[].class)
			return "base64";

		// extension types
		if (pType == void.class)
			return "ex:nil";
		if (pType == Byte.TYPE || pType == Byte.class)
			return "ex:i1";
		if (pType == Short.TYPE || pType == Short.class)
			return "ex:i2";
		if (pType == Long.TYPE || pType == Long.class)
			return "ex:i8";
		if (pType == Float.TYPE || pType == Float.class)
			return "ex:float";
		if (Node.class.isAssignableFrom(pType))
			return "ex:node";
		if (jaxbElementClass != null
			&&  jaxbElementClass.isAssignableFrom(pType)) {
			return "ex:jaxbElement";
		}
		if (Serializable.class.isAssignableFrom(pType))
			return "base64";

		// give up
		return null;
	}

	/** Returns a signature for the given methods.
	 * @param pMethods Methods, for which a signature is
	 * being queried.
	 * @return Signature string, or null, if no signature
	 * is available.
	 */
	public static String[][] getSignature(Method[] pMethods) {
        final List<String[]> result = new ArrayList<>();
		for (Method pMethod : pMethods) {
			String[] sig = getSignature(pMethod);
			if (sig != null) {
				result.add(sig);
			}
		}
        return result.toArray(new String[result.size()][]);
    }

    /** Returns a signature for the given methods.
     * @param pMethod Method, for which a signature is
     * being queried.
     * @return Signature string, or null, if no signature
     * is available.
     */
    public static String[] getSignature(Method pMethod) {    
		Class<?>[] paramClasses = pMethod.getParameterTypes();
		String[] sig = new String[paramClasses.length + 1];
		String s = getSignatureType(pMethod.getReturnType());
		if (s == null) {
			return null;
		}
		sig[0] = s;
		for (int i = 0;  i < paramClasses.length;  i++) {
			s = getSignatureType(paramClasses[i]);
			if (s == null) {
				return null;
			}
			sig[i+1] = s;
		}
		return sig;
	}

    /** Returns a help string for the given method, which
     * is applied to the given class.
     */
    public static String getMethodHelp(Class<?> pClass, Method[] pMethods) {
        final List<String> result = new ArrayList<>();
		for (Method pMethod : pMethods) {
			String help = getMethodHelp(pClass, pMethod);
			result.add(help);
		}
        switch (result.size()) {
            case 0:
                return null;
            case 1:
                return result.get(0);
            default:
                StringBuilder sb = new StringBuilder();
                for (int i = 0;  i < result.size();  i++) {
                    sb.append(i+1);
                    sb.append(": ");
                    sb.append(result.get(i));
                    sb.append("\n");
                }
                return sb.toString();
        }
    }

    /** Returns a help string for the given method, which
	 * is applied to the given class.
	 */
	public static String getMethodHelp(Class<?> pClass, Method pMethod) {
		StringBuilder sb = new StringBuilder();
		sb.append("Invokes the method ");
		sb.append(pClass.getName());
		sb.append(".");
		sb.append(pMethod.getName());
		sb.append("(");
		Class<?>[] paramClasses = pMethod.getParameterTypes();
		for (int i = 0;  i < paramClasses.length;  i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(paramClasses[i].getName());
		}
		sb.append(").");
		return sb.toString();
	}

    /** Returns a signature for the given parameter set. This is used
     * in error messages.
     */
    public static String getSignature(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0;  i < args.length;  i++) {
            if (i > 0) {
                sb.append(", ");
            }
            if (args[i] == null) {
                sb.append("null");
            } else {
                sb.append(args[i].getClass().getName());
            }
        }
        return sb.toString();
    }

    /**
     * Creates a new instance of <code>pClass</code>.
     */
    public static Object newInstance(Class<?> pClass) throws XmlRpcException {
        try {
            return pClass.getConstructor().newInstance();
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException e) {
            throw new XmlRpcException("Failed to instantiate class " + pClass.getName(), e);
        }
	}
}
