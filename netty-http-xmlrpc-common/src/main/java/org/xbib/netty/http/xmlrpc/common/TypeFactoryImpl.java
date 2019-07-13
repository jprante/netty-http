package org.xbib.netty.http.xmlrpc.common;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.xbib.netty.http.xmlrpc.common.parser.BigDecimalParser;
import org.xbib.netty.http.xmlrpc.common.parser.BigIntegerParser;
import org.xbib.netty.http.xmlrpc.common.parser.BooleanParser;
import org.xbib.netty.http.xmlrpc.common.parser.ByteArrayParser;
import org.xbib.netty.http.xmlrpc.common.parser.CalendarParser;
import org.xbib.netty.http.xmlrpc.common.parser.DateParser;
import org.xbib.netty.http.xmlrpc.common.parser.DoubleParser;
import org.xbib.netty.http.xmlrpc.common.parser.FloatParser;
import org.xbib.netty.http.xmlrpc.common.parser.I1Parser;
import org.xbib.netty.http.xmlrpc.common.parser.I2Parser;
import org.xbib.netty.http.xmlrpc.common.parser.I4Parser;
import org.xbib.netty.http.xmlrpc.common.parser.I8Parser;
import org.xbib.netty.http.xmlrpc.common.parser.MapParser;
import org.xbib.netty.http.xmlrpc.common.parser.NullParser;
import org.xbib.netty.http.xmlrpc.common.parser.ObjectArrayParser;
import org.xbib.netty.http.xmlrpc.common.parser.StringParser;
import org.xbib.netty.http.xmlrpc.common.parser.TypeParser;
import org.xbib.netty.http.xmlrpc.common.serializer.BigDecimalSerializer;
import org.xbib.netty.http.xmlrpc.common.serializer.BigIntegerSerializer;
import org.xbib.netty.http.xmlrpc.common.serializer.BooleanSerializer;
import org.xbib.netty.http.xmlrpc.common.serializer.ByteArraySerializer;
import org.xbib.netty.http.xmlrpc.common.serializer.CalendarSerializer;
import org.xbib.netty.http.xmlrpc.common.serializer.DateSerializer;
import org.xbib.netty.http.xmlrpc.common.serializer.DoubleSerializer;
import org.xbib.netty.http.xmlrpc.common.serializer.FloatSerializer;
import org.xbib.netty.http.xmlrpc.common.serializer.I1Serializer;
import org.xbib.netty.http.xmlrpc.common.serializer.I2Serializer;
import org.xbib.netty.http.xmlrpc.common.serializer.I4Serializer;
import org.xbib.netty.http.xmlrpc.common.serializer.I8Serializer;
import org.xbib.netty.http.xmlrpc.common.serializer.ListSerializer;
import org.xbib.netty.http.xmlrpc.common.serializer.MapSerializer;
import org.xbib.netty.http.xmlrpc.common.serializer.NullSerializer;
import org.xbib.netty.http.xmlrpc.common.serializer.ObjectArraySerializer;
import org.xbib.netty.http.xmlrpc.common.serializer.StringSerializer;
import org.xbib.netty.http.xmlrpc.common.serializer.TypeSerializer;
import org.xbib.netty.http.xmlrpc.common.serializer.XmlRpcWriter;
import org.w3c.dom.Node;
import org.xbib.netty.http.xmlrpc.common.util.NamespaceContextImpl;
import org.xbib.netty.http.xmlrpc.common.util.XmlRpcDateTimeDateFormat;
import org.xml.sax.SAXException;


/**
 * Default implementation of a type factory.
 */
public class TypeFactoryImpl implements TypeFactory {
    private static final TypeSerializer NULL_SERIALIZER = new NullSerializer();
    private static final TypeSerializer STRING_SERIALIZER = new StringSerializer();
    private static final TypeSerializer I4_SERIALIZER = new I4Serializer();
    private static final TypeSerializer BOOLEAN_SERIALIZER = new BooleanSerializer();
    private static final TypeSerializer DOUBLE_SERIALIZER = new DoubleSerializer();
    private static final TypeSerializer BYTE_SERIALIZER = new I1Serializer();
    private static final TypeSerializer SHORT_SERIALIZER = new I2Serializer();
    private static final TypeSerializer LONG_SERIALIZER = new I8Serializer();
    private static final TypeSerializer FLOAT_SERIALIZER = new FloatSerializer();
    private static final TypeSerializer BIGDECIMAL_SERIALIZER = new BigDecimalSerializer();
    private static final TypeSerializer BIGINTEGER_SERIALIZER = new BigIntegerSerializer();
    private static final TypeSerializer CALENDAR_SERIALIZER = new CalendarSerializer();

    private final XmlRpcController controller;
    private DateSerializer dateSerializer;

    /** Creates a new instance.
     * @param pController The controller, which operates the type factory.
     */
    public TypeFactoryImpl(XmlRpcController pController) {
        controller = pController;
    }

    /** Returns the controller, which operates the type factory.
     * @return The controller
     */
    public XmlRpcController getController() {
        return controller;
    }

    public TypeSerializer getSerializer(XmlRpcStreamConfig pConfig, Object pObject) throws SAXException {
        if (pObject == null) {
            if (pConfig.isEnabledForExtensions()) {
                return NULL_SERIALIZER;
            } else {
                throw new SAXException(new XmlRpcExtensionException("Null values aren't supported, if isEnabledForExtensions() == false"));
            }
        } else if (pObject instanceof String) {
            return STRING_SERIALIZER;
        } else if (pObject instanceof Byte) {
            if (pConfig.isEnabledForExtensions()) {
                return BYTE_SERIALIZER;
            } else {
                throw new SAXException(new XmlRpcExtensionException("Byte values aren't supported, if isEnabledForExtensions() == false"));
            }
        } else if (pObject instanceof Short) {
            if (pConfig.isEnabledForExtensions()) {
                return SHORT_SERIALIZER;
            } else {
                throw new SAXException(new XmlRpcExtensionException("Short values aren't supported, if isEnabledForExtensions() == false"));
            }
        } else if (pObject instanceof Integer) {
            return I4_SERIALIZER;
        } else if (pObject instanceof Long) {
            if (pConfig.isEnabledForExtensions()) {
                return LONG_SERIALIZER;
            } else {
                throw new SAXException(new XmlRpcExtensionException("Long values aren't supported, if isEnabledForExtensions() == false"));
            }
        } else if (pObject instanceof Boolean) {
            return BOOLEAN_SERIALIZER;
        } else if (pObject instanceof Float) {
            if (pConfig.isEnabledForExtensions()) {
                return FLOAT_SERIALIZER;
            } else {
                throw new SAXException(new XmlRpcExtensionException("Float values aren't supported, if isEnabledForExtensions() == false"));
            }
        } else if (pObject instanceof Double) {
            return DOUBLE_SERIALIZER;
        } else if (pObject instanceof Calendar) {
            if (pConfig.isEnabledForExtensions()) {
                return CALENDAR_SERIALIZER;
            } else {
                throw new SAXException(new XmlRpcExtensionException("Calendar values aren't supported, if isEnabledForExtensions() == false"));
            }
        } else if (pObject instanceof Date) {
            if (dateSerializer == null) {
                dateSerializer = new DateSerializer(new XmlRpcDateTimeDateFormat(){
                    private static final long serialVersionUID = 24345909123324234L;
                    protected TimeZone getTimeZone() {
                        return controller.getConfig().getTimeZone();
                    }
                });
            }
            return dateSerializer;
        } else if (pObject instanceof byte[]) {
            return new ByteArraySerializer();
        } else if (pObject instanceof Object[]) {
            return new ObjectArraySerializer(this, pConfig);
        } else if (pObject instanceof List) {
            return new ListSerializer(this, pConfig);
        } else if (pObject instanceof Map) {
            return new MapSerializer(this, pConfig);
        } else if (pObject instanceof Node) {
            throw new SAXException(new XmlRpcExtensionException("DOM nodes aren't supported"));
        } else if (pObject instanceof BigInteger) {
            if (pConfig.isEnabledForExtensions()) {
                return BIGINTEGER_SERIALIZER;
            } else {
                throw new SAXException(new XmlRpcExtensionException("BigInteger values aren't supported, if isEnabledForExtensions() == false"));
            }
        } else if (pObject instanceof BigDecimal) {
            if (pConfig.isEnabledForExtensions()) {
                return BIGDECIMAL_SERIALIZER;
            } else {
                throw new SAXException(new XmlRpcExtensionException("BigDecimal values aren't supported, if isEnabledForExtensions() == false"));
            }
        } else if (pObject instanceof Serializable) {
            throw new SAXException(new XmlRpcExtensionException("Serializable objects aren't supported"));
        } else {
            return null;
        }
    }

    public TypeParser getParser(XmlRpcStreamConfig pConfig, NamespaceContextImpl pContext, String pURI, String pLocalName) {
        if (XmlRpcWriter.EXTENSIONS_URI.equals(pURI)) {
            if (!pConfig.isEnabledForExtensions()) {
                return null;
            }
            if (NullSerializer.NIL_TAG.equals(pLocalName)) {
                return new NullParser();
            } else if (I1Serializer.I1_TAG.equals(pLocalName)) {
                return new I1Parser();
            } else if (I2Serializer.I2_TAG.equals(pLocalName)) {
                return new I2Parser();
            } else if (I8Serializer.I8_TAG.equals(pLocalName)) {
                return new I8Parser();
            } else if (FloatSerializer.FLOAT_TAG.equals(pLocalName)) {
                return new FloatParser();
            } else if (BigDecimalSerializer.BIGDECIMAL_TAG.equals(pLocalName)) {
                return new BigDecimalParser();
            } else if (BigIntegerSerializer.BIGINTEGER_TAG.equals(pLocalName)) {
                return new BigIntegerParser();
            } else if (CalendarSerializer.CALENDAR_TAG.equals(pLocalName)) {
                return new CalendarParser();
            }
        } else if ("".equals(pURI)) {
            if (I4Serializer.INT_TAG.equals(pLocalName)  ||  I4Serializer.I4_TAG.equals(pLocalName)) {
                return new I4Parser();
            } else if (BooleanSerializer.BOOLEAN_TAG.equals(pLocalName)) {
                return new BooleanParser();
            } else if (DoubleSerializer.DOUBLE_TAG.equals(pLocalName)) {
                return new DoubleParser();
            } else if (DateSerializer.DATE_TAG.equals(pLocalName)) {
                return new DateParser(new XmlRpcDateTimeDateFormat(){
                    private static final long serialVersionUID = 7585237706442299067L;
                    protected TimeZone getTimeZone() {
                        return controller.getConfig().getTimeZone();
                    }
                });
            } else if (ObjectArraySerializer.ARRAY_TAG.equals(pLocalName)) {
                return new ObjectArrayParser(pConfig, pContext, this);
            } else if (MapSerializer.STRUCT_TAG.equals(pLocalName)) {
                return new MapParser(pConfig, pContext, this);
            } else if (ByteArraySerializer.BASE_64_TAG.equals(pLocalName)) {
                return new ByteArrayParser();
            } else if (StringSerializer.STRING_TAG.equals(pLocalName)) {
                return new StringParser();
            }
        }
        return null;
    }
}
