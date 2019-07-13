package org.xbib.netty.http.xmlrpc.common;

/**
 * Interface of a configuration for a stream based transport.
 */
public interface XmlRpcStreamConfig extends XmlRpcConfig {

	/**
	 * Default encoding (UTF-8).
	 */
	String UTF8_ENCODING = "UTF-8";

	/**
	 * Returns the encoding being used for data encoding, when writing
	 * to a stream.
	 * @return Suggested encoding, or null, if the {@link #UTF8_ENCODING}
	 * is being used.
	 */
	String getEncoding();
}
