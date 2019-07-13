package org.xbib.netty.http.xmlrpc.common.util;

/**
 * <p>An instance of {@link java.text.Format}, which may be used to parse
 * and format <code>xs:date</code> values.</p>
 */
public class XsDateFormat extends XsDateTimeFormat {
	private static final long serialVersionUID = 3832621764093030707L;

	/** Creates a new instance.
     */
    public XsDateFormat() {
        super(true, false);
    }
}
