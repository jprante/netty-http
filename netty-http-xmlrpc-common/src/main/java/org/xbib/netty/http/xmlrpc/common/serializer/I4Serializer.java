package org.xbib.netty.http.xmlrpc.common.serializer;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A {@link TypeSerializer} for integers.
 */
public class I4Serializer extends TypeSerializerImpl {

	/**
	 * Tag name of an int value.
	 */
	public static final String INT_TAG = "int";

	/**
	 * Tag name of an i4 value.
	 */
	public static final String I4_TAG = "i4";

	@Override
	public void write(ContentHandler pHandler, Object pObject) throws SAXException {
		write(pHandler, I4_TAG, pObject.toString());
	}
}
