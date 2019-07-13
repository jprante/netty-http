package org.xbib.netty.http.xmlrpc.common;

import org.xbib.netty.http.xmlrpc.common.parser.TypeParser;
import org.xbib.netty.http.xmlrpc.common.serializer.TypeSerializer;
import org.xbib.netty.http.xmlrpc.common.util.NamespaceContextImpl;
import org.xml.sax.SAXException;

/**
 * A type factory creates serializers or handlers, based on the object
 * type.
 */
public interface TypeFactory {

    /**
     * Creates a serializer for the object <code>pObject</code>.
     * @param pConfig The request configuration.
     * @param pObject The object being serialized.
     * @return A serializer for <code>pObject</code>.
     * @throws SAXException Creating the serializer failed.
     */
    TypeSerializer getSerializer(XmlRpcStreamConfig pConfig, Object pObject) throws SAXException;

    /**
     * Creates a parser for a parameter or result object.
     * @param pConfig The request configuration.
     * @param pContext A namespace context, for looking up prefix mappings.
     * @param pURI The namespace URI of the element containing the parameter or result.
     * @param pLocalName The local name of the element containing the parameter or result.
     * @return The created parser.
     */
    TypeParser getParser(XmlRpcStreamConfig pConfig, NamespaceContextImpl pContext, String pURI, String pLocalName);
}
