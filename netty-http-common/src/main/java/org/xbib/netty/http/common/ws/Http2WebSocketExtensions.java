package org.xbib.netty.http.common.ws;

import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;
import io.netty.util.AsciiString;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Http2WebSocketExtensions {

    static final String HEADER_WEBSOCKET_EXTENSIONS_VALUE_PERMESSAGE_DEFLATE = "permessage-deflate";

    static final AsciiString HEADER_WEBSOCKET_EXTENSIONS_VALUE_PERMESSAGE_DEFLATE_ASCII =
            AsciiString.of(HEADER_WEBSOCKET_EXTENSIONS_VALUE_PERMESSAGE_DEFLATE);

    static final Pattern HEADER_WEBSOCKET_EXTENSIONS_PARAMETER_PATTERN =
            Pattern.compile("^([^=]+)(=[\\\"]?([^\\\"]+)[\\\"]?)?$");

    public static WebSocketExtensionData decode(CharSequence extensionHeader) {
        if (extensionHeader == null || extensionHeader.length() == 0) {
            return null;
        }
        AsciiString asciiExtensionHeader = (AsciiString) extensionHeader;
        for (AsciiString extension : asciiExtensionHeader.split(',')) {
            AsciiString[] extensionParameters = extension.split(';');
            AsciiString name = extensionParameters[0].trim();
            if (HEADER_WEBSOCKET_EXTENSIONS_VALUE_PERMESSAGE_DEFLATE_ASCII.equals(name)) {
                Map<String, String> parameters;
                if (extensionParameters.length > 1) {
                    parameters = new HashMap<>(extensionParameters.length - 1);
                    for (int i = 1; i < extensionParameters.length; i++) {
                        AsciiString parameter = extensionParameters[i].trim();
                        Matcher parameterMatcher =
                                HEADER_WEBSOCKET_EXTENSIONS_PARAMETER_PATTERN.matcher(parameter);
                        if (parameterMatcher.matches()) {
                            String key = parameterMatcher.group(1);
                            if (key != null) {
                                String value = parameterMatcher.group(3);
                                parameters.put(key, value);
                            }
                        }
                    }
                } else {
                    parameters = Collections.emptyMap();
                }
                return new WebSocketExtensionData(
                        HEADER_WEBSOCKET_EXTENSIONS_VALUE_PERMESSAGE_DEFLATE, parameters);
            }
        }
        return null;
    }

    public static String encode(WebSocketExtensionData extensionData) {
        String name = extensionData.name();
        Map<String, String> params = extensionData.parameters();
        if (params.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder(sizeOf(name, params));
        sb.append(name);
        for (Map.Entry<String, String> param : params.entrySet()) {
            sb.append(";");
            sb.append(param.getKey());
            String value = param.getValue();
            if (value != null) {
                sb.append("=");
                sb.append(value);
            }
        }
        return sb.toString();
    }

    static int sizeOf(String extensionName, Map<String, String> extensionParameters) {
        int size = extensionName.length();
        for (Map.Entry<String, String> param : extensionParameters.entrySet()) {
            size += param.getKey().length() + 1;
            String value = param.getValue();
            if (value != null) {
                size += value.length() + 1;
            }
        }
        return size;
    }
}
