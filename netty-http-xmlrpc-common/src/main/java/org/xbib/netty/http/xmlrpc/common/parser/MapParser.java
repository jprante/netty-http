package org.xbib.netty.http.xmlrpc.common.parser;

import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;

import org.xbib.netty.http.xmlrpc.common.TypeFactory;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamConfig;
import org.xbib.netty.http.xmlrpc.common.serializer.MapSerializer;
import org.xbib.netty.http.xmlrpc.common.serializer.TypeSerializerImpl;
import org.xbib.netty.http.xmlrpc.common.util.NamespaceContextImpl;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * {@link org.xbib.netty.http.xmlrpc.common.parser.TypeParser} implementation
 * for maps.
 */
public class MapParser extends RecursiveTypeParserImpl {

    private int level = 0;

    private StringBuffer nameBuffer = new StringBuffer();

    private Object nameObject;

    private Map<Object, Object> map;

    private boolean inName, inValue, doneValue;

    /** Creates a new instance.
     * @param pConfig The request or response configuration.
     * @param pContext The namespace context.
     * @param pFactory The factory.
     */
    public MapParser(XmlRpcStreamConfig pConfig,
                     NamespaceContextImpl pContext,
                     TypeFactory pFactory) {
        super(pConfig, pContext, pFactory);
    }

    protected void addResult(Object pResult) throws SAXException {
        if (inName) {
            nameObject = pResult;
        } else {
            if (nameObject == null) {
                throw new SAXParseException("Invalid state: Expected name",
                        getDocumentLocator());
            } else {
                if (map.containsKey(nameObject)) {
                    throw new SAXParseException("Duplicate name: " + nameObject,
                            getDocumentLocator());
                } else {
                    map.put(nameObject, pResult);
                }
            }
        }
    }

    public void startDocument() throws SAXException {
        super.startDocument();
        level = 0;
        map = new HashMap<>();
        inValue = inName = false;
    }

    public void characters(char[] pChars, int pOffset, int pLength) throws SAXException {
        if (inName  &&  !inValue) {
            nameBuffer.append(pChars, pOffset, pLength);
        } else {
            super.characters(pChars, pOffset, pLength);
        }
    }

    public void ignorableWhitespace(char[] pChars, int pOffset, int pLength) throws SAXException {
        if (inName) {
            characters(pChars, pOffset, pLength);
        } else {
            super.ignorableWhitespace(pChars, pOffset, pLength);
        }
    }

    public void startElement(String pURI, String pLocalName, String pQName,
                             Attributes pAttrs) throws SAXException {
        switch (level++) {
            case 0:
                if (!"".equals(pURI)  ||  !MapSerializer.STRUCT_TAG.equals(pLocalName)) {
                    throw new SAXParseException("Expected " + MapSerializer.STRUCT_TAG + ", got "
                            + new QName(pURI, pLocalName),
                            getDocumentLocator());
                }
                break;
            case 1:
                if (!"".equals(pURI)  ||  !MapSerializer.MEMBER_TAG.equals(pLocalName)) {
                    throw new SAXParseException("Expected " + MapSerializer.MEMBER_TAG + ", got "
                            + new QName(pURI, pLocalName),
                            getDocumentLocator());
                }
                doneValue = inName = inValue = false;
                nameObject = null;
                nameBuffer.setLength(0);
                break;
            case 2:
                if (doneValue) {
                    throw new SAXParseException("Expected /" + MapSerializer.MEMBER_TAG
                            + ", got " + new QName(pURI, pLocalName),
                            getDocumentLocator());
                }
                if ("".equals(pURI)  &&  MapSerializer.NAME_TAG.equals(pLocalName)) {
                    if (nameObject == null) {
                        inName = true;
                    } else {
                        throw new SAXParseException("Expected " + TypeSerializerImpl.VALUE_TAG
                                + ", got " + new QName(pURI, pLocalName),
                                getDocumentLocator());
                    }
                } else if ("".equals(pURI)  &&  TypeSerializerImpl.VALUE_TAG.equals(pLocalName)) {
                    if (nameObject == null) {
                        throw new SAXParseException("Expected " + MapSerializer.NAME_TAG
                                + ", got " + new QName(pURI, pLocalName),
                                getDocumentLocator());
                    } else {
                        inValue = true;
                        startValueTag();
                    }

                }
                break;
            case 3:
                if (inName  &&  "".equals(pURI)  &&  TypeSerializerImpl.VALUE_TAG.equals(pLocalName)) {
                    if (cfg.isEnabledForExtensions()) {
                        inValue = true;
                        startValueTag();
                    } else {
                        throw new SAXParseException("Expected /" + MapSerializer.NAME_TAG
                                + ", got " + new QName(pURI, pLocalName),
                                getDocumentLocator());
                    }
                } else {
                    super.startElement(pURI, pLocalName, pQName, pAttrs);
                }
                break;
            default:
                super.startElement(pURI, pLocalName, pQName, pAttrs);
                break;
        }
    }

    public void endElement(String pURI, String pLocalName, String pQName) throws SAXException {
        switch (--level) {
            case 0:
                setResult(map);
                break;
            case 1:
                break;
            case 2:
                if (inName) {
                    inName = false;
                    if (nameObject == null) {
                        nameObject = nameBuffer.toString();
                    } else {
                        for (int i = 0;  i < nameBuffer.length();  i++) {
                            if (!Character.isWhitespace(nameBuffer.charAt(i))) {
                                throw new SAXParseException("Unexpected non-whitespace character in member name",
                                        getDocumentLocator());
                            }
                        }
                    }
                } else if (inValue) {
                    endValueTag();
                    doneValue = true;
                }
                break;
            case 3:
                if (inName  &&  inValue  &&  "".equals(pURI)  &&  TypeSerializerImpl.VALUE_TAG.equals(pLocalName)) {
                    endValueTag();
                } else {
                    super.endElement(pURI, pLocalName, pQName);
                }
                break;
            default:
                super.endElement(pURI, pLocalName, pQName);
        }
    }
}
