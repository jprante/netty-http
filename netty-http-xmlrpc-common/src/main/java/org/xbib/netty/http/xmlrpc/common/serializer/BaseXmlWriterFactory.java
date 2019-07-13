package org.xbib.netty.http.xmlrpc.common.serializer;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamConfig;
import org.xbib.netty.http.xmlrpc.common.util.XMLWriter;
import org.xbib.netty.http.xmlrpc.common.util.XMLWriterImpl;
import org.xml.sax.ContentHandler;

/**
 * The default instance of {@link XmlWriterFactory} creates
 * instances of {@link XMLWriterImpl}.
 */
public class BaseXmlWriterFactory implements XmlWriterFactory {

    protected XMLWriter newXmlWriter() {
        return new XMLWriterImpl();
    }

    @Override
    public ContentHandler getXmlWriter(XmlRpcStreamConfig pConfig, OutputStream pStream)
            throws XmlRpcException {
        XMLWriter xw = newXmlWriter();
        xw.setDeclarating(true);
        String enc = pConfig.getEncoding();
        if (enc == null) {
            enc = XmlRpcStreamConfig.UTF8_ENCODING;
        }
        xw.setEncoding(enc);
        xw.setIndenting(false);
        xw.setFlushing(true);
        try {
            xw.setWriter(new BufferedWriter(new OutputStreamWriter(pStream, enc)));
        } catch (UnsupportedEncodingException e) {
            throw new XmlRpcException("Unsupported encoding: " + enc, e);
        }
        return xw;
    }
}
