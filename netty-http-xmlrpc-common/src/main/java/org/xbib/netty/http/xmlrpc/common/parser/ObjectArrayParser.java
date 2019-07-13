package org.xbib.netty.http.xmlrpc.common.parser;

import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import org.xbib.netty.http.xmlrpc.common.TypeFactory;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamConfig;
import org.xbib.netty.http.xmlrpc.common.serializer.ObjectArraySerializer;
import org.xbib.netty.http.xmlrpc.common.serializer.TypeSerializerImpl;
import org.xbib.netty.http.xmlrpc.common.util.NamespaceContextImpl;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Parser for an array of objects, as created by
 * {@link ObjectArraySerializer}.
 */
public class ObjectArrayParser extends RecursiveTypeParserImpl {

    private int level = 0;

    private List<Object> list;

    /** Creates a new instance.
     * @param pContext The namespace context.
     * @param pConfig The request or response configuration.
     * @param pFactory The type factory.
     */
    public ObjectArrayParser(XmlRpcStreamConfig pConfig,
                             NamespaceContextImpl pContext,
                             TypeFactory pFactory) {
        super(pConfig, pContext, pFactory);
    }

    public void startDocument() throws SAXException {
        level = 0;
        list = new ArrayList<>();
        super.startDocument();
    }

    protected void addResult(Object pValue) {
        list.add(pValue);
    }

    public void endElement(String pURI, String pLocalName, String pQName) throws SAXException {
        switch (--level) {
            case 0:
                setResult(list.toArray());
                break;
            case 1:
                break;
            case 2:
                endValueTag();
                break;
            default:
                super.endElement(pURI, pLocalName, pQName);
        }
    }

    public void startElement(String pURI, String pLocalName, String pQName, Attributes pAttrs) throws SAXException {
        switch (level++) {
            case 0:
                if (!"".equals(pURI)  ||  !ObjectArraySerializer.ARRAY_TAG.equals(pLocalName)) {
                    throw new SAXParseException("Expected array element, got "
                            + new QName(pURI, pLocalName),
                            getDocumentLocator());
                }
                break;
            case 1:
                if (!"".equals(pURI)  ||  !ObjectArraySerializer.DATA_TAG.equals(pLocalName)) {
                    throw new SAXParseException("Expected data element, got "
                            + new QName(pURI, pLocalName),
                            getDocumentLocator());
                }
                break;
            case 2:
                if (!"".equals(pURI)  ||  !TypeSerializerImpl.VALUE_TAG.equals(pLocalName)) {
                    throw new SAXParseException("Expected data element, got "
                            + new QName(pURI, pLocalName),
                            getDocumentLocator());
                }
                startValueTag();
                break;
            default:
                super.startElement(pURI, pLocalName, pQName, pAttrs);
                break;
        }
    }

}
