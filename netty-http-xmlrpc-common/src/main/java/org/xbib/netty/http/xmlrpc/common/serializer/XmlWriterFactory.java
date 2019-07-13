package org.xbib.netty.http.xmlrpc.common.serializer;

import java.io.OutputStream;

import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamConfig;
import org.xbib.netty.http.xmlrpc.common.util.XMLWriter;
import org.xml.sax.ContentHandler;

/** This factory is responsible for creating instances of
 * {@link XMLWriter}.
 */
public interface XmlWriterFactory {
	/** Creates a new instance of {@link ContentHandler},
	 * writing to the given {@link OutputStream}.
	 * @return A SAX handler
	 * @param pStream The destination stream.
	 * @param pConfig The request or response configuration.
	 * @throws XmlRpcException Creating the handler failed.
	 */
	public ContentHandler getXmlWriter(XmlRpcStreamConfig pConfig,
									   OutputStream pStream) throws XmlRpcException;
}
