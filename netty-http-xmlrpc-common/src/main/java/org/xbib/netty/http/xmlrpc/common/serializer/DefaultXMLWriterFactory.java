package org.xbib.netty.http.xmlrpc.common.serializer;

import java.io.OutputStream;
import java.io.StringWriter;

import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamConfig;
import org.xbib.netty.http.xmlrpc.common.util.CharSetXMLWriter;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;

/**
 * The default implementation of {@link XmlWriterFactory}
 * tests, whether the {@link CharSetXmlWriterFactory}
 * is usable.
 */
public class DefaultXMLWriterFactory implements XmlWriterFactory {
	private final XmlWriterFactory factory;

	/**
	 * Creates a new instance.
	 */
	public DefaultXMLWriterFactory() {
		XmlWriterFactory xwf;
		try {
			CharSetXMLWriter csw = new CharSetXMLWriter();
			StringWriter sw = new StringWriter();
			csw.setWriter(sw);
			csw.startDocument();
			csw.startElement("", "test", "test", new AttributesImpl());
			csw.endElement("", "test", "test");
			csw.endDocument();
			xwf = new CharSetXmlWriterFactory();
		} catch (Throwable t) {
			xwf = new BaseXmlWriterFactory();
		}
		factory = xwf;
	}

	public ContentHandler getXmlWriter(XmlRpcStreamConfig pConfig,
									   OutputStream pStream) throws XmlRpcException {
		return factory.getXmlWriter(pConfig, pStream);
	}
}
