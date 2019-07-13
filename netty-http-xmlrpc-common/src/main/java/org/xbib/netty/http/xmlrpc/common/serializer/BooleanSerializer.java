package org.xbib.netty.http.xmlrpc.common.serializer;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A {@link TypeSerializer} for booleans.
 */
public class BooleanSerializer extends TypeSerializerImpl {

	/**
	 * Tag name of a boolean value.
	 */
	public static final String BOOLEAN_TAG = "boolean";

	private static final char[] TRUE = new char[]{'1'};

	private static final char[] FALSE = new char[]{'0'};

	@Override
	public void write(ContentHandler pHandler, Object pObject) throws SAXException {
		write(pHandler, BOOLEAN_TAG, (Boolean) pObject ? TRUE : FALSE);
	}
}
