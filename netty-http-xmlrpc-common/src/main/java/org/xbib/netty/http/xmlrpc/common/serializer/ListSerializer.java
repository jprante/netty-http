package org.xbib.netty.http.xmlrpc.common.serializer;

import java.util.List;

import org.xbib.netty.http.xmlrpc.common.TypeFactory;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamConfig;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A {@link TypeSerializer} for lists.
 */
public class ListSerializer extends ObjectArraySerializer {

    /** Creates a new instance.
     * @param pTypeFactory The factory being used for creating serializers.
     * @param pConfig The configuration being used for creating serializers.
     */
    public ListSerializer(TypeFactory pTypeFactory, XmlRpcStreamConfig pConfig) {
        super(pTypeFactory, pConfig);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void writeData(ContentHandler pHandler, Object pObject) throws SAXException {
        List<Object> data = (List) pObject;
        for (Object datum : data) {
            writeObject(pHandler, datum);
        }
    }
}
