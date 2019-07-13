package org.xbib.netty.http.xmlrpc.common;

/**
 * A {@link TypeConverterFactory} is called for creating instances
 * of {@link TypeConverter}.
 */
public interface TypeConverterFactory {

    /**
     * Creates an instance of {@link TypeFactory}, which may be
     * used to create instances of the given class.
     * @param pClass class
     * @return type converter
     */
    TypeConverter getTypeConverter(Class<?> pClass);
}
