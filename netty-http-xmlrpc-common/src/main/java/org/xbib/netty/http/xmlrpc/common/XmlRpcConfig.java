package org.xbib.netty.http.xmlrpc.common;

import java.util.TimeZone;

public interface XmlRpcConfig {

    /**
     * Returns, whether support for extensions are enabled.
     * By default, extensions are disabled and your client is
     * interoperable with other XML-RPC implementations.
     * Interoperable XML-RPC implementations are those, which
     * are compliant to the
     * <a href="http://www.xmlrpc.org/spec">XML-RPC Specification</a>.
     * @return Whether extensions are enabled or not.
     */
    boolean isEnabledForExtensions();

    /** Returns the timezone, which is used to interpret date/time
     * values. Defaults to {@link TimeZone#getDefault()}.
     * @return time zone
     */
    TimeZone getTimeZone();
}
