package org.xbib.netty.http.xmlrpc.common;

import java.util.List;
import java.util.Vector;

/**
 * A {@link TypeConverter} is used when actually calling the
 * handler method or actually returning the result object. It's
 * purpose is to convert a single parameter or the return value
 * from a generic representation (for example an array of objects)
 * to an alternative representation, which is actually used in
 * the methods signature (for example {@link List}, or
 * {@link Vector}.
 */
public interface TypeConverter {

    /**
     * Returns true whether the {@link TypeConverter} is
     * ready to handle the given object. If so,
     * {@link #convert(Object)} may be called.
     * @param pObject object
     * @return true
     */
    boolean isConvertable(Object pObject);

    /**
     * Converts the given object into the required
     * representation.
     * @param pObject object
     * @return object
     */
    Object convert(Object pObject);

    /**
     * Converts the given object into its generic
     * representation.
     * @param result result
     * @return object
     */
    Object backConvert(Object result);
}
