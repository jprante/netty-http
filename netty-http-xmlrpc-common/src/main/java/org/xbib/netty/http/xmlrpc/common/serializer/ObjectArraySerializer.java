package org.xbib.netty.http.xmlrpc.common.serializer;

import org.xbib.netty.http.xmlrpc.common.TypeFactory;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamConfig;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/** A {@link TypeSerializer} for object arrays.
 */
public class ObjectArraySerializer extends TypeSerializerImpl {
    /** Tag name of an array value.
     */
    public static final String ARRAY_TAG = "array";
    /** Tag name of an arrays data.
     */
    public static final String DATA_TAG = "data";

    private final XmlRpcStreamConfig config;
    private final TypeFactory typeFactory;

    /** Creates a new instance.
     * @param pTypeFactory The factory being used for creating serializers.
     * @param pConfig The configuration being used for creating serializers.
     */
    public ObjectArraySerializer(TypeFactory pTypeFactory, XmlRpcStreamConfig pConfig) {
        typeFactory = pTypeFactory;
        config = pConfig;
    }
    protected void writeObject(ContentHandler pHandler, Object pObject) throws SAXException {
        TypeSerializer ts = typeFactory.getSerializer(config, pObject);
        if (ts == null) {
            throw new SAXException("Unsupported Java type: " + pObject.getClass().getName());
        }
        ts.write(pHandler, pObject);
    }
    protected void writeData(ContentHandler pHandler, Object pObject) throws SAXException {
        Object[] data = (Object[]) pObject;
        for (int i = 0;  i < data.length;  i++) {
            writeObject(pHandler, data[i]);
        }
    }
    public void write(final ContentHandler pHandler, Object pObject) throws SAXException {
        pHandler.startElement("", VALUE_TAG, VALUE_TAG, ZERO_ATTRIBUTES);
        pHandler.startElement("", ARRAY_TAG, ARRAY_TAG, ZERO_ATTRIBUTES);
        pHandler.startElement("", DATA_TAG, DATA_TAG, ZERO_ATTRIBUTES);
        writeData(pHandler, pObject);
        pHandler.endElement("", DATA_TAG, DATA_TAG);
        pHandler.endElement("", ARRAY_TAG, ARRAY_TAG);
        pHandler.endElement("", VALUE_TAG, VALUE_TAG);
    }
}