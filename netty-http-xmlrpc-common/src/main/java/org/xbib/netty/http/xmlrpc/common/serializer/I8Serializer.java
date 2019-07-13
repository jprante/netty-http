package org.xbib.netty.http.xmlrpc.common.serializer;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A {@link TypeSerializer} for longs.
 */
public class I8Serializer extends TypeSerializerImpl {

	/**
	 * Tag name of an i8 value.
	 */
	public static final String I8_TAG = "i8";

	/**
	 * Fully qualified name of an i8 value.
	 */
	public static final String EX_I8_TAG = "ex:i8";

	@Override
	public void write(ContentHandler pHandler, Object pObject) throws SAXException {
		write(pHandler, I8_TAG, EX_I8_TAG, pObject.toString());
	}
}
