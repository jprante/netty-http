package org.xbib.netty.http.xmlrpc.common.serializer;

import java.util.Map;

import org.xbib.netty.http.xmlrpc.common.TypeFactory;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamConfig;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A {@link TypeSerializer} for maps.
 */
public class MapSerializer extends TypeSerializerImpl {
    /** Tag name of a maps struct tag.
     */
    public static final String STRUCT_TAG = "struct";

    /** Tag name of a maps member tag.
     */
    public static final String MEMBER_TAG = "member";

    /** Tag name of a maps members name tag.
     */
    public static final String NAME_TAG = "name";

    private final XmlRpcStreamConfig config;
    private final TypeFactory typeFactory;

    /** Creates a new instance.
     * @param pTypeFactory The factory being used for creating serializers.
     * @param pConfig The configuration being used for creating serializers.
     */
    public MapSerializer(TypeFactory pTypeFactory, XmlRpcStreamConfig pConfig) {
        typeFactory = pTypeFactory;
        config = pConfig;
    }

    protected void writeEntry(ContentHandler pHandler, Object pKey, Object pValue) throws SAXException {
        pHandler.startElement("", MEMBER_TAG, MEMBER_TAG, ZERO_ATTRIBUTES);
        pHandler.startElement("", NAME_TAG, NAME_TAG, ZERO_ATTRIBUTES);
        if (config.isEnabledForExtensions()  &&  !(pKey instanceof String)) {
            writeValue(pHandler, pKey);
        } else {
            String key = pKey.toString();
            pHandler.characters(key.toCharArray(), 0, key.length());
        }
        pHandler.endElement("", NAME_TAG, NAME_TAG);
        writeValue(pHandler, pValue);
        pHandler.endElement("", MEMBER_TAG, MEMBER_TAG);
    }

    private void writeValue(ContentHandler pHandler, Object pValue)
            throws SAXException {
        TypeSerializer ts = typeFactory.getSerializer(config, pValue);
        if (ts == null) {
            throw new SAXException("Unsupported Java type: " + pValue.getClass().getName());
        }
        ts.write(pHandler, pValue);
    }

    @SuppressWarnings("unchecked")
    protected void writeData(ContentHandler pHandler, Object pData) throws SAXException {
        Map<Object, Object> map = (Map) pData;
        for (Object o : map.entrySet()) {
            Map.Entry<Object, Object> entry = (Map.Entry) o;
            writeEntry(pHandler, entry.getKey(), entry.getValue());
        }
    }

    public void write(final ContentHandler pHandler, Object pObject) throws SAXException {
        pHandler.startElement("", VALUE_TAG, VALUE_TAG, ZERO_ATTRIBUTES);
        pHandler.startElement("", STRUCT_TAG, STRUCT_TAG, ZERO_ATTRIBUTES);
        writeData(pHandler, pObject);
        pHandler.endElement("", STRUCT_TAG, STRUCT_TAG);
        pHandler.endElement("", VALUE_TAG, VALUE_TAG);
    }
}
