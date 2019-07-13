package org.xbib.netty.http.xmlrpc.common.serializer;

import org.xbib.netty.http.xmlrpc.common.util.CharSetXMLWriter;
import org.xbib.netty.http.xmlrpc.common.util.XMLWriter;

/**
 * An implementation of {@link XmlWriterFactory},
 * which creates instances of {@link CharSetXMLWriter}.
 */
public class CharSetXmlWriterFactory extends BaseXmlWriterFactory {

    @Override
    protected XMLWriter newXmlWriter() {
        return new CharSetXMLWriter();
    }
}
