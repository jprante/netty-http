package org.xbib.netty.http.xmlrpc.common.util;

/**
 * <p>An instance of {@link java.text.Format}, which may be used to parse
 * and format <code>xs:time</code> values.</p>
 */
public class XsTimeFormat extends XsDateTimeFormat {

    private static final long serialVersionUID = 3906931187925727282L;

	/** Creates a new instance.
     */
    public XsTimeFormat() {
        super(false, true);
    }
}
