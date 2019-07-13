package org.xbib.netty.http.xmlrpc.common.serializer;

import java.util.Base64;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A {@link TypeSerializer} for byte arrays.
 */
public class ByteArraySerializer extends TypeSerializerImpl {

	/**
	 * Tag name of a base64 value.
	 */
	public static final String BASE_64_TAG = "base64";

	@Override
	public void write(final ContentHandler pHandler, Object pObject) throws SAXException {
		pHandler.startElement("", VALUE_TAG, VALUE_TAG, ZERO_ATTRIBUTES);
		pHandler.startElement("", BASE_64_TAG, BASE_64_TAG, ZERO_ATTRIBUTES);
		byte[] buffer = (byte[]) pObject;
		if (buffer.length > 0) {
			String encoded = Base64.getEncoder().encodeToString(buffer);
			char[] charBuffer = encoded.toCharArray();
			pHandler.characters(charBuffer, 0, charBuffer.length);
		}
		pHandler.endElement("", BASE_64_TAG, BASE_64_TAG);
		pHandler.endElement("", VALUE_TAG, VALUE_TAG);
	}
}
